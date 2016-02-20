package oni;

class InstagramMedia {
    private String code;
    private int date;
    private int width;
    private int height;
    private boolean is_video;
    private String display_src;

    public InstagramMedia(){}

    private InstagramMedia(String code, int date, int width, int height, boolean is_video, String display_src)
    {
        this.code = code;
        this.date = date;
        this.width = width;
        this.height = height;
        this.is_video = is_video;
        this.display_src = display_src;
    }

    public static InstagramMedia build( String code, int date, int width, int height, boolean is_video,
                                        String display_src )
    {
        return new InstagramMedia(code, date, width, height, is_video, display_src);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getDate() {
        return date;
    }

    public void setDate(int date) {
        this.date = date;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public boolean is_video() {
        return is_video;
    }

    public void setIs_video(boolean is_video) {
        this.is_video = is_video;
    }

    public String getDisplay_src() {
        return display_src;
    }

    public void setDisplay_src(String display_src) {
        this.display_src = display_src;
    }
}