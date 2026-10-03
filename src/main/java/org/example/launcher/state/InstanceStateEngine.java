package org.example.launcher.state;

import org.example.launcher.model.Instance;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class InstanceStateEngine {
    private InstanceStateEngine(){}

    public static InstanceState inspect(Instance instance) {
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

        int mods=countFiles(root.resolve("mods"));
        int configs=countFiles(root.resolve("config"));
        total++;
        if(!findZeroByteFiles(root.resolve("mods"))) passed++;
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