package oni;

import org.apache.commons.cli.*;

import java.net.InetSocketAddress;
import java.net.Proxy;

public class ConsoleDownloader {

    private static InstagramDownloader downloader = new InstagramDownloader();

    public static void main(String[] args) {
        Options options = new Options();

        final Option helpOption = Option.builder("h")
                .longOpt("help")
                .desc("Print help")
                .build();

        final Option userOption = Option.builder("u")
                .longOpt("username")
                .desc("Download media from specified instagram user")
                .hasArg()
                .argName("username")
                .build();

        final Option urlOption = Option.builder("a")
                .longOpt("url")
                .desc("Download single media from a url")
                .hasArg()
                .argName("url")
                .build();

        final Option proxyOption = Option.builder("x")
                .longOpt("http_proxy")
                .desc("Set http proxy, hostname:port")
                .hasArg()
                .argName("http proxy")
                .build();

        final Option limitOption = Option.builder("m")
                .longOpt("limit")
                .desc("Set download count limit")
                .hasArg()
                .argName("limit")
                .build();

        final Option updateOption = Option.builder("p")
                .longOpt("update")
                .desc("Update specified user's media")
                .build();

        OptionGroup inputGroup = new OptionGroup();
        inputGroup.setRequired(true);
        inputGroup
                .addOption(userOption)
                .addOption(urlOption);

        options
                .addOptionGroup(inputGroup)
                .addOption(limitOption)
                .addOption(proxyOption)
                .addOption(helpOption)
                .addOption(updateOption);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter helpFormatter = new HelpFormatter();

        try {
            CommandLine cli = parser.parse(options, args);
            int limit = 0;
            Proxy proxy = Proxy.NO_PROXY;
            boolean isUpdate = false;

            if ( cli.hasOption("h") ) {
                helpFormatter.printHelp(downloader.getClass().getSimpleName(), options);
            }

            if ( cli.hasOption("m") ) {
                try {
                    limit = Integer.parseInt( cli.getOptionValue("m") );
                    if ( limit <= 0 ) {
                        System.out.println("Invalid arg: " + cli.getOptionValue("m"));
                        System.exit(1);
                    }
                }
                catch ( NumberFormatException e ) {
                    System.out.println("Invalid arg: " + cli.getOptionValue("m"));
                    System.exit(1);
                }
            }

            if ( cli.hasOption("x") ) {
                String x = cli.getOptionValue("x");
                try {
                    String hostname = x.split(":")[0];
                    int port = Integer.parseInt( x.split(":")[1] );
                    proxy = new Proxy( Proxy.Type.HTTP, new InetSocketAddress(hostname, port) );
                }
                catch ( Exception e ) {
                    System.out.println("Invalid arg: " + x);
                    System.exit(1);
                }
            }

            if ( cli.hasOption("p") ) {
                isUpdate = true;
            }

            if ( cli.hasOption("u") ) {
                downloader.parseUser( cli.getOptionValue("u"), proxy, limit, isUpdate );
            }
            else if ( cli.hasOption("a") ) {
                downloader.parseURL(cli.getOptionValue("a"), proxy);
            }
        } catch (ParseException e) {
            helpFormatter.printHelp(downloader.getClass().getSimpleName(), options, true);
        }
    }
}
