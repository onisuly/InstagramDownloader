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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by onisuly on 2/10/16.
 * Instagram Downloader
 * JDK 8
 */
public class InstagramDownloader {

    private long point = 1;
    private long count = 0;
    private long size = 0;

    public void parseURL(String url, Proxy proxy) {
        String HTMLContent = readContent(url, proxy);
        Matcher videoMatcher = Pattern
                .compile("<meta property=\"og:video:secure_url\" content=\"(.+?)\" />")
                .matcher( HTMLContent );
        Matcher imageMatcher = Pattern
                .compile("<meta property=\"og:image\" content=\"(.+?)\" />")
                .matcher( HTMLContent );
        String mediaAddress = null;
        if ( videoMatcher.find() ) {
            mediaAddress = videoMatcher.group(1);
        }
        else if ( imageMatcher.find() ) {
            mediaAddress = imageMatcher.group(1);
        }

        if ( mediaAddress == null ) {
            System.out.println( "Parse media failed!" );
        }
        else {
            String extension = mediaAddress.split("\\?ig_cache_key")[0];
            extension = extension.substring( extension.lastIndexOf(".") );

            Pattern pattern = Pattern.compile("<script type=\"text/javascript\">window._sharedData = (.+)?;</script>");
            Matcher matcher = pattern.matcher( HTMLContent );
            Optional<String> jsonContent = Optional.empty();
            if ( matcher.find() ) {
                jsonContent = Optional.of( matcher.group(1) );
            }
            if ( !jsonContent.isPresent() ) {
                System.out.println("Something goes wrong!");
                return;
            }
            JSONObject jsonObject = new JSONObject( jsonContent.get() );
            JSONObject media = jsonObject.getJSONObject("entry_data").getJSONArray("PostPage").getJSONObject(0).
                    getJSONObject("media");

            String username = media.getJSONObject("owner").getString("username");
            File dir = new File(username);
            if ( ( !dir.exists() && !dir.mkdir() ) || ( dir.exists() && dir.isFile() ) ) {
                System.out.println( "Cannot create directory: " + dir.getAbsolutePath() );
                return;
            }
            int date = media.getInt("date");
            LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(date, 0, ZoneOffset.UTC);
            String formattedDate = localDateTime.format( DateTimeFormatter.ofPattern("yyyyMMddhhmmss") );
            String fileName = formattedDate + extension;

            File outFile = new File( dir.getAbsolutePath() + File.separator + fileName );
            downloadFile( mediaAddress, outFile, proxy );
            System.out.println( "Completed!" );
        }
    }

    public void parseUser(String username, Proxy proxy, long limit, boolean isUpdate) {
        boolean has_next_page;
        String end_cursor = "";
        point = 1;
        count = 0;
        size = 0;

        String lastUpdateTime  = null;
        if ( isUpdate ) {
            File dir = new File( username );
            if ( dir.exists() ) {
                List<String> list = Arrays.asList( dir.list() );
                lastUpdateTime = list.stream()
                        .map( s -> s.substring(0, 14) )
                        .filter( s -> s.matches("^[0-9]{14}$") )
                        .sorted( Comparator.reverseOrder() )
                        .findFirst()
                        .orElseGet(() -> null);
            }
        }

        outer:
        do {
            String HTMLContent = readContent("https://www.instagram.com/" + username + "/?max_id=" + end_cursor, proxy);
            Pattern pattern = Pattern.compile("<script type=\"text/javascript\">window._sharedData = (.+)?;</script>");
            Matcher matcher = pattern.matcher( HTMLContent );
            Optional<String> jsonContent = Optional.empty();
            if ( matcher.find() ) {
                jsonContent = Optional.of( matcher.group(1) );
            }

            if ( !jsonContent.isPresent() ) {
                System.out.println("Something goes wrong!");
                return;
            }

            File dir = new File( username );
            if ( end_cursor.equals("") && (
                    ( !dir.exists() && !dir.mkdir() ) || ( dir.exists() && dir.isFile() )
            ))
            {
                System.out.println( "Cannot create directory: " + dir.getAbsolutePath() );
                return;
            }

            JSONObject jsonObject = new JSONObject( jsonContent.get() );
            JSONObject user = jsonObject.getJSONObject("entry_data").getJSONArray("ProfilePage").getJSONObject(0)
                    .getJSONObject("user");

            count = user.getJSONObject("media").getLong("count");
            if ( count == 0  ) {
                System.out.println( "No media found!" );
                return;
            }
            count = limit > 0 ? Math.min(limit, count) : count;

            if ( end_cursor.equals("") ) {
                System.out.println("Downloading: " + username + "\n" + "Total: " + count + " item(s)");
            }

            JSONArray nodes = user.getJSONObject("media").getJSONArray("nodes");
            JSONObject page_info = user.getJSONObject("media").getJSONObject("page_info");
            has_next_page = page_info.getBoolean("has_next_page");
            end_cursor = page_info.getString("end_cursor");

            for ( int i = 0; i < nodes.length(); ++i ) {
                if ( point > count ) break outer;
                JSONObject node = nodes.getJSONObject(i);
                String code = node.getString("code");
                LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(node.getInt("date"), 0, ZoneOffset.UTC);
                String date = localDateTime.format( DateTimeFormatter.ofPattern("yyyyMMddhhmmss") );
                boolean is_video = node.getBoolean("is_video");

                if ( isUpdate ) {
                    if ( lastUpdateTime != null && date.compareTo(lastUpdateTime) <= 0 ) break outer;
                }

                if ( is_video ) {
                    new Thread( () -> {
                        String videoContent = readContent("https://www.instagram.com/p/" + code, proxy);
                        Matcher videoMatcher = pattern.matcher( videoContent );
                        if ( videoMatcher.find() ) {
                            String videoJson = videoMatcher.group(1);
                            JSONObject videoObject = new JSONObject(videoJson);
                            String video_url = videoObject.getJSONObject("entry_data").getJSONArray("PostPage")
                                    .getJSONObject(0).getJSONObject("media").getString("video_url");
                            new Thread( () -> downloadMedia(video_url, dir, date, proxy) ).run();
                        }
                    }).run();
                }
                else {
                    String display_src = node.getString("display_src");
                    new Thread( () -> downloadMedia(display_src, dir, date, proxy) ).run();
                }
            }
        } while ( has_next_page );
    }

    private void downloadMedia(String address, File dir, String caption, Proxy proxy ) {
        String fileName = address.split("\\?ig_cache_key")[0];
        fileName = caption + fileName.substring( fileName.lastIndexOf(".") );
        File outFile = new File(dir.getAbsolutePath() + File.separator + fileName);

        long fileSize = downloadFile(address, outFile, proxy);
        size += fileSize;

        //Clear entire line, reference: http://www.climagic.org/mirrors/VT100_Escape_Codes.html
        System.out.printf("%c[2K", 27);

        System.out.printf("\r%.2f%% Completed, %s Total Downloaded!", 100.0 * point++ / count,
                humanReadableByteCount(size, true));

        if ( point > count ) System.out.print("\n\n");
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
