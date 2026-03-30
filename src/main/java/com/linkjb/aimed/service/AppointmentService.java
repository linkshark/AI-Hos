package com.linkjb.aimed.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linkjb.aimed.entity.Appointment;

public interface AppointmentService extends IService<Appointment> {
    Appointment getOne(Appointment appointment);
}
