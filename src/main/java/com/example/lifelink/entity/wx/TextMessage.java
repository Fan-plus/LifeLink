package com.example.lifelink.entity.wx;

public class TextMessage extends BaseMessage {
    private String Content;

    // Getters and Setters
    public String getContent() {
        return Content;
    }

    public void setContent(String content) {
        Content = content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TextMessage that = (TextMessage) o;
        return Content != null ? Content.equals(that.Content) : that.Content == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (Content != null ? Content.hashCode() : 0);
        return result;
    }
}
