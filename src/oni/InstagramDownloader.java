package oni;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InstagramDownloader {

    public void parseURL(String url, Proxy proxy) {

        Pattern pattern = Pattern.compile("www.instagram.com/p/([^/]+)");
        Matcher matcher = pattern.matcher(url);

        String code;
        if ( matcher.find() ) {
            code = matcher.group(1);
        }
        else {
            System.out.println("Invalid Url: " + url);
            return;
        }

        InstagramMedia media = parseOneResource(code, proxy);
        if ( media == null ) {
            System.out.println("Something goes wrong!");
            return;
        }

        String extension = media.getDisplay_src().split("\\?ig_cache_key")[0];
        extension = extension.substring( extension.lastIndexOf(".") );
        int date = media.getDate();
        LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(date, 0, ZoneOffset.UTC);
        String formattedDate = localDateTime.format( DateTimeFormatter.ofPattern("yyyyMMddhhmmss") );
        String fileName = formattedDate + extension;

        File outFile = new File( fileName );
        downloadFile( media.getDisplay_src(), outFile, proxy );
        System.out.println( "Completed!" );
    }

    public void parseUser(String username, Proxy proxy, int limit, boolean isUpdate) {
        boolean has_next_page;
        String end_cursor = "";

        int mediaPoint = 1;
        int mediaCount;
        long downloadedSize = 0;

        File dir = new File( username );
        if ( ( !dir.exists() && !dir.mkdir() ) || ( dir.exists() && dir.isFile() ) )
        {
            System.out.println( "Cannot create directory: " + dir.getAbsolutePath() );
            return;
        }

        JSONObject user = readUserJson(username, end_cursor, proxy);
        if ( user == null ) {
            System.out.println("Something goes wrong!");
            return;
        }

        mediaCount = user.getJSONObject("media").getInt("count");

        if ( mediaCount == 0  ) {
            System.out.println( "No media found!" );
            return;
        }

        if ( limit > 0 ) mediaCount = Integer.min(mediaCount, limit);

        InstagramMedia[] medias = new InstagramMedia[mediaCount];

        int addMediaPoint = 0;
        int loopCount = 0;
        outer:
        do {
            System.out.print("\033[2K"); //Erase line content
            System.out.print( "\rParsing" + "...".substring(0, loopCount++ % 3 + 1) );
            JSONArray nodes = user.getJSONObject("media").getJSONArray("nodes");
            JSONObject page_info = user.getJSONObject("media").getJSONObject("page_info");
            has_next_page = page_info.getBoolean("has_next_page");
            end_cursor = page_info.getString("end_cursor");

            for ( int i = 0; i < nodes.length(); ++i ) {
                JSONObject node = nodes.getJSONObject(i);

                String code = node.getString("code");
                int date = node.getInt("date");
                int width = node.getJSONObject("dimensions").getInt("width");
                int height = node.getJSONObject("dimensions").getInt("height");
                boolean is_video = node.getBoolean("is_video");
                String display_src = node.getString("display_src");

                if ( isUpdate ) {
                    File userDir = new File( username );
                    String lastUpdateTime  = null;
                    if ( userDir.exists() ) {
                        List<String> list = Arrays.asList( userDir.list() );
                        lastUpdateTime = list.stream()
                                .map( s -> s.substring(0, 14) )
                                .filter( s -> s.matches("^[0-9]{14}$") )
                                .sorted( Comparator.reverseOrder() )
                                .findFirst()
                                .orElseGet(() -> null);
                    }
                    LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(date, 0, ZoneOffset.UTC);
                    String strDate = localDateTime.format( DateTimeFormatter.ofPattern("yyyyMMddhhmmss") );
                    if ( lastUpdateTime != null && strDate.compareTo(lastUpdateTime) <= 0 ) {
                        mediaCount = addMediaPoint;
                        break outer;
                    }
                }

                medias[addMediaPoint++] =  new InstagramMedia(code, date, width, height, is_video, display_src);
                if ( addMediaPoint >= mediaCount ) break outer;
            }

            user = readUserJson(username, end_cursor, proxy);
        } while ( has_next_page );

        System.out.print("\033[2K"); //Erase line content
        System.out.println( "\rParse completed!" );
        System.out.printf("Downloading: %s\nTotal: %d item(s)\n", username, mediaCount);

        for ( int i = 0; i < mediaCount; ++i ) {
            String code = medias[i].getCode();
            int date = medias[i].getDate();
            boolean is_video = medias[i].is_video();
            String display_src = is_video ? parseOneResource(code, proxy).getDisplay_src() : medias[i].getDisplay_src();

            String fileName = display_src.split("\\?ig_cache_key")[0];

            LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(date, 0, ZoneOffset.UTC);
            String caption = localDateTime.format( DateTimeFormatter.ofPattern("yyyyMMddhhmmss") );
            fileName = caption + fileName.substring( fileName.lastIndexOf(".") );

            File outFile = new File(dir.getAbsolutePath() + File.separator + fileName);

            long fileSize = downloadFile(display_src, outFile, proxy);
            downloadedSize += fileSize;

            System.out.print("\033[2K"); //Erase line content
            System.out.printf("\r%.2f%% Completed, %s Total Downloaded!", 100.0 * mediaPoint++ / mediaCount,
                    humanReadableByteCount(downloadedSize, true));

            if ( mediaPoint > mediaCount ) System.out.print("\n");
        }
    }

    private JSONObject readUserJson(String username, String end_cursor, Proxy proxy) {
        String HTMLContent = readContent("https://www.instagram.com/" + username + "/?max_id=" + end_cursor, proxy);
        Pattern pattern = Pattern.compile("<script type=\"text/javascript\">window._sharedData = (.+)?;</script>");
        Matcher matcher = pattern.matcher( HTMLContent );

        JSONObject user = null;
        if ( matcher.find() ) {
            String jsonContent = matcher.group(1);
            JSONObject jsonObject = new JSONObject( jsonContent );
            user = jsonObject.getJSONObject("entry_data").getJSONArray("ProfilePage").getJSONObject(0)
                    .getJSONObject("user");
        }
        return user;
    }

    private InstagramMedia parseOneResource( String code, Proxy proxy ) {
        InstagramMedia media = null;
        String mediaContent = readContent("https://www.instagram.com/p/" + code, proxy);
        Pattern pattern = Pattern.compile(
                "<script type=\"text/javascript\">window._sharedData = (.+)?;</script>"
        );
        Matcher mediaMatcher = pattern.matcher( mediaContent );
        if ( mediaMatcher.find() ) {
            String mediaJson = mediaMatcher.group(1);
            JSONObject videoObject = new JSONObject(mediaJson);
            JSONObject PostPage =  videoObject.getJSONObject("entry_data").getJSONArray("PostPage").getJSONObject(0);
            int date = PostPage.getJSONObject("media").getInt("date");
            int width = PostPage.getJSONObject("media").getJSONObject("dimensions").getInt("width");
            int height = PostPage.getJSONObject("media").getJSONObject("dimensions").getInt("height");
            boolean is_video = PostPage.getJSONObject("media").getBoolean("is_video");
            String display_src = is_video ? PostPage.getJSONObject("media").getString("video_url") :
                    PostPage.getJSONObject("media").getString("display_src");
            media = new InstagramMedia(code, date, width, height, is_video, display_src);
        }
        return media;
    }

    private long downloadFile(String address, File outFile, Proxy proxy ) {
        long fileSize = 0;
        try {
            URLConnection connection = new URL(address).openConnection(proxy);
            connection.connect();
            fileSize = connection.getContentLength();
            InputStream is = connection.getInputStream();
            final int BUFFER_SIZE = 1024;
            byte[] BUFFER = new byte[BUFFER_SIZE];
            int bytesRead;
            OutputStream os = new FileOutputStream(outFile);
            while ( ( bytesRead = is.read(BUFFER, 0, BUFFER_SIZE) ) != -1 ) {
                os.write(BUFFER, 0, bytesRead);
            }
            os.flush();
            os.close();
            is.close();

        }
        catch ( IOException e ) {
            e.printStackTrace();
        }
        return fileSize;
    }

    private String readContent( String address, Proxy proxy ) {
        String HTMLContent = "";
        try {
            URLConnection connection = new URL(address).openConnection(proxy);
            connection.connect();
            InputStream is = connection.getInputStream();
            BufferedReader br = new BufferedReader( new InputStreamReader(is) );
            final int BUFFER_SIZE = 1024;
            char[] buffer = new char[BUFFER_SIZE];
            int charsRead;
            StringBuilder sb = new StringBuilder();
            while ( ( charsRead = br.read(buffer, 0, BUFFER_SIZE) ) != -1 ) {
                sb.append(buffer, 0, charsRead);
            }
            is.close();
            HTMLContent = sb.toString();
        }
        catch ( IOException e ) {
            e.printStackTrace();
        }

        return HTMLContent;
    }

    private String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

}
