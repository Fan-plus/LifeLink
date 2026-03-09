package com.example.lifelink.api;

public class VideoRequest {
    private String video_subject; // 主题
    private String video_script;  // 脚本
    private String video_aspect;  // 比例
    private int video_count;
    private String voice_name;    // 配音音色
    private String video_source;  // 视频素材来源

    public VideoRequest(String script) {
        this.video_subject = "AI 智能讲解";
        this.video_script = script;
        this.video_aspect = "16:9";
        this.video_count = 1;
        this.voice_name = "siliconflow:FunAudioLLM/CosyVoice2-0.5B:alex-Male";
        this.video_source = "pixabay";
    }

    // Getters and Setters
    public String getVideo_subject() { return video_subject; }
    public void setVideo_subject(String video_subject) { this.video_subject = video_subject; }
    public String getVideo_script() { return video_script; }
    public void setVideo_script(String video_script) { this.video_script = video_script; }
    public String getVideo_aspect() { return video_aspect; }
    public void setVideo_aspect(String video_aspect) { this.video_aspect = video_aspect; }
    public int getVideo_count() { return video_count; }
    public void setVideo_count(int video_count) { this.video_count = video_count; }
    public String getVoice_name() { return voice_name; }
    public void setVoice_name(String voice_name) { this.voice_name = voice_name; }
    public String getVideo_source() { return video_source; }
    public void setVideo_source(String video_source) { this.video_source = video_source; }
}
