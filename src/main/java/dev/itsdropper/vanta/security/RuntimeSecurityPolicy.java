package dev.itsdropper.vanta.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.json.VantaJson;
import dev.itsdropper.vanta.launcher.MinecraftLocator;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Signed, centrally applied runtime mitigations. Policy data cannot inject arbitrary JVM arguments. */
public final class RuntimeSecurityPolicy {
    private static final URI MANIFEST=URI.create("https://raw.githubusercontent.com/ItsDropper/Vanta/main/security/runtime-policy.json");
    private static final URI SIGNATURE=URI.create("https://raw.githubusercontent.com/ItsDropper/Vanta/main/security/runtime-policy.sig");
    private static final String PUBLIC_KEY="mzWz277tLKqkSi2NzgaH9k/uKLHcMw/SJzP6X/JeBVE=";
    private static final int LIMIT=65536;
    private static final ObjectMapper JSON=VantaJson.strictMapper();
    private static final RuntimeSecurityPolicy INSTANCE=new RuntimeSecurityPolicy();
    private final Path cache=MinecraftLocator.getVantaDirectoryPath().resolve("security");
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Vanta-Security-Policy");t.setDaemon(true);return t;});
    private final AtomicBoolean refreshing=new AtomicBoolean();
    private final AtomicReference<List<Rule>> rules=new AtomicReference<>(List.of());
    private volatile String status="Built-in Java4Shell mitigation active; remote policy not checked.";

    private RuntimeSecurityPolicy(){loadCache();}
    public static RuntimeSecurityPolicy getInstance(){return INSTANCE;}
    public void initialize(){refreshAsync();}
    public void refreshAsync(){if(refreshing.compareAndSet(false,true))CompletableFuture.runAsync(this::refresh,executor).whenComplete((v,e)->refreshing.set(false));}
    public String getStatus(){return status;}

    public List<String> argumentsFor(int javaMajor,String architecture){
        String arch=normalizeArchitecture(architecture);
        if(javaMajor<1||arch==null)return List.of();
        Set<String> result=new LinkedHashSet<>();
        // Mandatory offline fallback: remote policy can never turn this off.
        if(javaMajor>=17&&arch.equals("x64"))result.add("-XX:-UseBASE64Intrinsics");
        for(Rule r:rules.get())if(javaMajor>=r.min&&javaMajor<=r.max&&r.arch.contains(arch))
            for(Action a:r.actions)result.add(a.argument);
        return List.copyOf(result);
    }

    private void refresh(){
        try{
            byte[] payload=request(MANIFEST);
            byte[] sig=Base64.getDecoder().decode(new String(request(SIGNATURE),StandardCharsets.US_ASCII).trim());
            List<Rule> verified=parseAndVerify(payload,sig,Clock.systemUTC());
            String digest=sha(payload);checkSequence(verifiedSequence(payload),digest);
            Files.createDirectories(cache);
            writeAtomic(cache.resolve("runtime-policy.cache"),JSON.writeValueAsBytes(Map.of(
                    "payload",Base64.getEncoder().encodeToString(payload),"signature",Base64.getEncoder().encodeToString(sig))));
            writeAtomic(cache.resolve("runtime-policy.state"),JSON.writeValueAsBytes(Map.of("sequence",verifiedSequence(payload),"sha256",digest)));
            rules.set(verified);status="Signed runtime security policy "+verifiedSequence(payload)+" verified and active.";
            System.out.println("[Vanta Security] "+status);
        }catch(Exception e){status="Using built-in/cached protections; policy refresh failed: "+e.getClass().getSimpleName();System.err.println("[Vanta Security] "+status);}
    }
    private byte[] request(URI uri)throws Exception{
        HttpRequest req=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).header("User-Agent","Vanta-Launcher-Runtime-Security").GET().build();
        HttpResponse<InputStream> res=http.send(req,HttpResponse.BodyHandlers.ofInputStream());
        try(InputStream in=res.body()){
            if(res.statusCode()!=200)throw new IOException("Policy HTTP "+res.statusCode());
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n,total=0;
            while((n=in.read(b))!=-1){total+=n;if(total>LIMIT)throw new IOException("Policy too large");out.write(b,0,n);}
            if(total==0)throw new IOException("Empty policy response");return out.toByteArray();
        }
    }
    private void loadCache(){
        Path file=cache.resolve("runtime-policy.cache");
        try{
            if(!Files.isRegularFile(file)||Files.size(file)>LIMIT*2L)return;
            JsonNode e=JSON.readTree(Files.readAllBytes(file));
            byte[] p=Base64.getDecoder().decode(text(e,"payload")),s=Base64.getDecoder().decode(text(e,"signature"));
            List<Rule> parsed=parseAndVerify(p,s,Clock.systemUTC());checkSequence(verifiedSequence(p),sha(p));
            rules.set(parsed);
            Path state=cache.resolve("runtime-policy.state");
            if(!Files.exists(state)){
                Files.createDirectories(cache);
                writeAtomic(state,JSON.writeValueAsBytes(Map.of("sequence",verifiedSequence(p),"sha256",sha(p))));
            }
            status="Cached signed runtime policy "+verifiedSequence(p)+" verified.";
        }catch(Exception e){status="Cached policy rejected; built-in protections remain active.";}
    }
    private void checkSequence(long seq,String digest)throws Exception{
        Path state=cache.resolve("runtime-policy.state");if(!Files.isRegularFile(state))return;
        if(Files.size(state)>4096)throw new IOException("Invalid policy state");
        JsonNode s=JSON.readTree(Files.readAllBytes(state));long old=s.path("sequence").asLong(-1);
        if(seq<old||(seq==old&&!s.path("sha256").asText("").equals(digest)))throw new IOException("Policy rollback or sequence reuse rejected");
    }
    private static void writeAtomic(Path file,byte[] bytes)throws IOException{
        Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");Files.write(tmp,bytes);
        try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(tmp);}
    }
    static List<Rule> parseAndVerify(byte[] payload,byte[] signature,Clock clock)throws Exception{
        PublicKey key=Ed25519Signatures.decodePublicKey(PUBLIC_KEY);
        return parseAndVerify(payload,signature,clock,key);
    }
    static List<Rule> parseAndVerify(byte[] payload,byte[] signature,Clock clock,PublicKey key)throws Exception{
        if(payload==null||payload.length==0||payload.length>LIMIT)throw new IOException("Invalid policy size");
        try {
            Ed25519Signatures.verify(payload, signature, key);
        } catch (GeneralSecurityException invalidSignature) {
            throw new IOException("Invalid policy signature", invalidSignature);
        }
        JsonNode root=JSON.readTree(payload);
        if(root.path("schemaVersion").asInt(-1)!=1||root.path("sequence").asLong(0)<1||!root.path("rules").isArray())throw new IOException("Invalid policy schema");
        Instant issued=Instant.parse(text(root,"issuedAt")),expires=Instant.parse(text(root,"expiresAt")),now=clock.instant();
        if(issued.isAfter(now.plus(1,ChronoUnit.DAYS))||!expires.isAfter(now)||!expires.isAfter(issued)||expires.isAfter(issued.plus(366,ChronoUnit.DAYS)))throw new IOException("Invalid policy validity");
        List<Rule> result=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(JsonNode n:root.path("rules")){
            String id=text(n,"id");int min=n.path("minimumJavaMajor").asInt(-1),max=n.path("maximumJavaMajor").asInt(-1);
            if(!id.matches("[A-Z0-9][A-Z0-9._-]{2,79}")||!ids.add(id)||min<8||max<min||max>999||!n.path("architectures").isArray()||!n.path("mitigations").isArray())throw new IOException("Invalid policy rule");
            Set<String> arch=new HashSet<>();for(JsonNode a:n.path("architectures")){String v=a.asText("");if(!Set.of("x64","aarch64").contains(v))throw new IOException("Unsupported architecture");arch.add(v);}
            Set<Action> actions=new HashSet<>();for(JsonNode a:n.path("mitigations"))try{actions.add(Action.valueOf(a.asText("")));}catch(Exception e){throw new IOException("Unknown mitigation action",e);}
            if(arch.isEmpty()||actions.isEmpty())throw new IOException("Empty policy rule");
            result.add(new Rule(min,max,Set.copyOf(arch),Set.copyOf(actions)));
        }
        if(result.isEmpty())throw new IOException("Policy has no rules");return List.copyOf(result);
    }
    private static String text(JsonNode n,String field)throws IOException{JsonNode v=n.get(field);if(v==null||!v.isTextual()||v.asText().isBlank())throw new IOException("Missing field "+field);return v.asText();}
    private static long verifiedSequence(byte[] p)throws IOException{return JSON.readTree(p).path("sequence").asLong();}
    private static String sha(byte[] p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(p));}
    static String normalizeArchitecture(String a){if(a==null)return null;return switch(a.toLowerCase(Locale.ROOT).replace('-','_')){case "amd64","x86_64","x64"->"x64";case "arm64","aarch64"->"aarch64";default->null;};}
    enum Action{DISABLE_BASE64_INTRINSICS("-XX:-UseBASE64Intrinsics");final String argument;Action(String a){argument=a;}}
    record Rule(int min,int max,Set<String> arch,Set<Action> actions){}
}
