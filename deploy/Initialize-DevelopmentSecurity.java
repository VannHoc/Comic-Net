import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Local development key material. Never prints a key. */
public class InitializeDevelopmentSecurity {
    public static void main(String[] args) throws Exception {
        Path directory=Path.of(args.length==0?".local/security":args[0]).toAbsolutePath();
        Files.createDirectories(directory);
        Path privateFile=directory.resolve("jwt-private.pem"),publicFile=directory.resolve("jwt-public.pem");
        if(Files.exists(privateFile)!=Files.exists(publicFile))throw new IllegalStateException("Incomplete key pair; inspect local security directory.");
        if(!Files.exists(privateFile)){
            var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);var pair=generator.generateKeyPair();
            write(privateFile,pem("PRIVATE KEY",pair.getPrivate().getEncoded()));
            write(publicFile,pem("PUBLIC KEY",pair.getPublic().getEncoded()));
        }
        var random=new SecureRandom();
        if(!Files.exists(directory.resolve("mail.key"))){byte[] key=new byte[32];random.nextBytes(key);write(directory.resolve("mail.key"),HexFormat.of().formatHex(key));}
        System.out.println("Development security material ready at "+directory+". No secret printed.");
    }
    private static String pem(String name,byte[] bytes){return "-----BEGIN "+name+"-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(bytes)+"\n-----END "+name+"-----\n";}
    private static void write(Path file,String content)throws Exception{Files.writeString(file,content,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);}
}
