package com.jobradar.entity;

import jakarta.persistence.*;

/** 用户资料（个人中心 & 自动填充）。字段与前端 JSON 一致，可直接返回。 */
@Entity
@Table(name = "profile")
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;     // 所属用户（每人一份资料）

    private String name;
    private String phone;
    private String email;
    private String gender;
    private String school;
    private String education;
    private String major;
    private String gradYear;
    private String gpa;
    private String intentCity;
    private String plan;

    public Profile() {}

    public Profile(String name, String phone, String email, String gender, String school,
                   String education, String major, String gradYear, String gpa,
                   String intentCity, String plan) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.gender = gender;
        this.school = school;
        this.education = education;
        this.major = major;
        this.gradYear = gradYear;
        this.gpa = gpa;
        this.intentCity = intentCity;
        this.plan = plan;
    }

    // ── getters / setters ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getSchool() { return school; }
    public void setSchool(String school) { this.school = school; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }
    public String getGradYear() { return gradYear; }
    public void setGradYear(String gradYear) { this.gradYear = gradYear; }
    public String getGpa() { return gpa; }
    public void setGpa(String gpa) { this.gpa = gpa; }
    public String getIntentCity() { return intentCity; }
    public void setIntentCity(String intentCity) { this.intentCity = intentCity; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
}
