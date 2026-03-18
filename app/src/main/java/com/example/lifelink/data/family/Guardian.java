package com.example.lifelink.data.family;

public class Guardian {
    private long id;
    private String name;
    private String phone;
    private String email;
    private String relation;
    private int permissionLevel; // 0: 仅查看预警, 1: 查看全部数据

    public Guardian(long id, String name, String phone, String email, String relation, int permissionLevel) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.relation = relation;
        this.permissionLevel = permissionLevel;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getRelation() { return relation; }
    public int getPermissionLevel() { return permissionLevel; }
}
