package com.linkjb.aimed.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
public class Appointment {
    // 主键生成策略
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String idCard;
    private String department;
    private String date;
    private String time;
    private String doctorName;

    public Appointment() {
    }

    public Appointment(Long id, String username, String idCard, String department, String date, String time, String doctorName) {
        this.id = id;
        this.username = username;
        this.idCard = idCard;
        this.department = department;
        this.date = date;
        this.time = time;
        this.doctorName = doctorName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIdCard() {
        return idCard;
    }

    public void setIdCard(String idCard) {
        this.idCard = idCard;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    @Override
    public String toString() {
        return "Appointment{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", idCard='" + idCard + '\'' +
                ", department='" + department + '\'' +
                ", date='" + date + '\'' +
                ", time='" + time + '\'' +
                ", doctorName='" + doctorName + '\'' +
                '}';
    }
}
