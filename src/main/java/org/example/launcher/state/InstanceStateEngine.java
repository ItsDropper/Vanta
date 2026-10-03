package org.example.launcher.state;

import org.example.launcher.model.Instance;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.launcher.MinecraftLocator;

public final class InstanceStateEngine {
    private InstanceStateEngine(){}

    public static List<SharedState> inspectSharedResources() {
        int workers = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 32));
        return List.of(
                inspectShared("Libraries", MinecraftLocator.getLibrariesDirectory(), true, workers),
                inspectShared("Assets", MinecraftLocator.getVantaDirectory().resolve("assets"), false, workers)
        );
    }

    public static SharedState inspectShared(String name, Path root, boolean validateArchives) {
        int workers = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 32));
        return inspectShared(name, root, validateArchives, workers);
    }

    public static SharedState inspectShared(String name, Path root, boolean validateArchives, int workers) {
        if (root == null || !Files.isDirectory(root)) {
            return new SharedState(name, SharedState.Level.ATTENTION, 0, 1, "Directory is missing and may need to be rebuilt.");
        }

        int files = 0;
        int broken = 0;
        try (var stream = Files.walk(root)) {
            var paths = stream.filter(Files::isRegularFile).toList();
            files = paths.size();

            AtomicInteger brokenFiles = new AtomicInteger();
            ForkJoinPool pool = new ForkJoinPool(Math.max(1, workers));
            try {
                pool.submit(() -> paths.parallelStream().forEach(path -> {
                    try {
                        if (Files.size(path) == 0) {
                            brokenFiles.incrementAndGet();
                        } else if (validateArchives
                                && path.getFileName().toString().toLowerCase().endsWith(".jar")) {
                            try (ZipFile ignored = new ZipFile(path.toFile())) {}
                        }
                    } catch (Exception ignored) {
                        brokenFiles.incrementAndGet();
                    }
                })).get();
            } finally {
                pool.shutdown();
            }
            broken = brokenFiles.get();
        } catch (Exception ignored) {
            return new SharedState(name, SharedState.Level.BROKEN, files, broken + 1, "Vanta could not completely scan this directory.");
        }

        SharedState.Level level = broken == 0 ? SharedState.Level.HEALTHY : SharedState.Level.BROKEN;
        String summary = broken == 0 ? files + " files verified." : broken + " broken or empty file" + (broken == 1 ? "" : "s") + " detected.";
        return new SharedState(name, level, files, broken, summary);
    }

    public static InstanceState inspect(Instance instance) {
        int workers = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 32));
        return inspect(instance, workers);
    }

    public static InstanceState inspect(Instance instance, int workers) {
        if(instance==null || instance.getDirectory()==null)
            return new InstanceState(InstanceState.Level.BROKEN,"INSTANCE UNAVAILABLE","Vanta could not inspect this instance.",0,1,0,0,"");

        Path root=instance.getDirectory();
        int total=0, passed=0;
        List<String> problems=new ArrayList<>();

        total++; if(Files.isDirectory(root)) passed++; else problems.add("Instance directory is missing");
        total++; if(Files.isRegularFile(root.resolve("instance.json"))) passed++; else problems.add("Instance metadata is missing");
        total++; if(instance.getMinecraftVersion()!=null && !instance.getMinecraftVersion().isBlank()) passed++; else problems.add("Minecraft version is not configured");
        total++; if(instance.getLoader()!=null && !instance.getLoader().isBlank()) passed++; else problems.add("Loader is not configured");
        total++; if(!Files.exists(root.resolve(".installing"))) passed++; else problems.add("Installation is incomplete");

        for(String name:new String[]{"mods","config","resourcepacks","shaderpacks","saves","logs","screenshots"}){
            total++;
            if(Files.isDirectory(root.resolve(name))) passed++; else problems.add("Missing "+name+" directory");
        }

        int mods;
        int configs;
        boolean emptyMod;

        ForkJoinPool pool = new ForkJoinPool(Math.max(1, Math.min(workers, 3)));
        try {
            Future<Integer> modsFuture = pool.submit(() -> countFiles(root.resolve("mods")));
            Future<Integer> configsFuture = pool.submit(() -> countFiles(root.resolve("config")));
            Future<Boolean> emptyModFuture = pool.submit(() -> findZeroByteFiles(root.resolve("mods")));
            mods = modsFuture.get();
            configs = configsFuture.get();
            emptyMod = emptyModFuture.get();
        } catch (Exception ignored) {
            mods = countFiles(root.resolve("mods"));
            configs = countFiles(root.resolve("config"));
            emptyMod = findZeroByteFiles(root.resolve("mods"));
        } finally {
            pool.shutdown();
        }

        total++;
        if(!emptyMod) passed++;
        else problems.add("Empty mod file detected");

        InstanceState.Level level=problems.isEmpty()?InstanceState.Level.HEALTHY:(problems.size()==1?InstanceState.Level.ATTENTION:InstanceState.Level.BROKEN);
        String title=level==InstanceState.Level.HEALTHY?"HEALTHY":level==InstanceState.Level.ATTENTION?"NEEDS ATTENTION":"BROKEN";
        String summary=problems.isEmpty()?"Everything Vanta can verify is in order.":problems.get(0);

        return new InstanceState(level,title,summary,passed,total,mods,configs,fingerprint(root));
    }

    private static int countFiles(Path directory){
        if(!Files.isDirectory(directory)) return 0;
        try(var stream=Files.walk(directory)){return (int)stream.filter(Files::isRegularFile).count();}
        catch(IOException ignored){return 0;}
    }

    private static boolean findZeroByteFiles(Path directory){
        if(!Files.isDirectory(directory)) return false;
        try(var stream=Files.walk(directory)){
            return stream.filter(Files::isRegularFile).anyMatch(path->{try{return Files.size(path)==0;}catch(IOException ignored){return false;}});
        }catch(IOException ignored){return false;}
    }

    private static String fingerprint(Path root){
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            if(!Files.exists(root)) return "";

            // State identity must stay fast. Do not walk saves, logs, screenshots,
            // or other potentially huge instance data just to display a fingerprint.
            List<Path> roots=new ArrayList<>();
            roots.add(root.resolve("instance.json"));
            for(String name:new String[]{"mods","config","resourcepacks","shaderpacks"}){
                roots.add(root.resolve(name));
            }

            for(Path target:roots){
                if(Files.isRegularFile(target)){
                    updateFingerprint(digest,root,target);
                    continue;
                }
                if(!Files.isDirectory(target)) continue;

                try(var stream=Files.walk(target,2)){
                    stream.filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(Path::toString))
                            .forEach(path->updateFingerprint(digest,root,path));
                }
            }

            byte[] bytes=digest.digest();
            StringBuilder out=new StringBuilder(16);
            for(int i=0;i<8;i++) out.append(String.format("%02x",bytes[i]));
            return out.toString().toUpperCase();
        }catch(Exception ignored){return "";}
    }

    private static void updateFingerprint(MessageDigest digest,Path root,Path path){
        try{
            digest.update(root.relativize(path).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(longBytes(Files.size(path)));
            digest.update(longBytes(Files.getLastModifiedTime(path).toMillis()));
        }catch(IOException ignored){}
    }

    private static byte[] longBytes(long value){
        return new byte[]{(byte)(value>>>56),(byte)(value>>>48),(byte)(value>>>40),(byte)(value>>>32),(byte)(value>>>24),(byte)(value>>>16),(byte)(value>>>8),(byte)value};
    }
}