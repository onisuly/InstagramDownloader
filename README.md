# InstagramDownloader
Download media from Instagram

Usage:
```
usage: InstagramDownloader -a <url> | -u <username> [-h] [-m <limit>] [-p] [-x <http proxy>]
 -a,--url <url>                 Download single media from a url
 -h,--help                      Print help
 -m,--limit <limit>             Set download count limit
 -p,--update                    Update specified user's media
 -u,--username <username>       Download media from specified instagram user
 -x,--http_proxy <http proxy>   Set http proxy, hostname:port
```

Examples:
```
java -jar InstagramDownloader.jar -u marcella_the_naeun -x 127.0.0.1:1081 -m 10
java -jar InstagramDownloader.jar -a https://www.instagram.com/p/BBQ9mojtx85/ -x 127.0.0.1:1081
```
