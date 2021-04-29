package net.shinkuchan.minecraft.ConfigSync;

public class Client {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Invalid argument");
            System.out.println("Usage: java -jar minecraft_config_sync.jar SERVER_URL [LOCAL_ROOT]");
        } else {
            try {
                ServerConnector sc = new ServerConnector(args[0]);
                System.out.println("RETRIEVING LATEST DATA FROM SERVER...");
                sc.fetch();
                String prefix = args.length > 1 ? args[1] : "./";
                System.out.println("WRITING FILES TO DIRECTORY: " + prefix + " ...");
                sc.getFSTree().write(prefix);
                System.out.println("EVERYTHING IS DONE!");
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("ERROR OCCURRED!");
            }
        }
    }
}
