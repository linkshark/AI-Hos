package com.linkjb.aimed.tools;

import com.alibaba.fastjson.JSON;
import com.linkjb.aimed.entity.AppUser;
import com.linkjb.aimed.entity.Appointment;
import com.linkjb.aimed.service.AppointmentService;
import com.linkjb.aimed.service.AppUserService;
import com.linkjb.aimed.service.ChatSessionUserBindingService;
import com.linkjb.aimed.service.MailSenderService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
public class AppointmentTools {
    private static final Logger log = LoggerFactory.getLogger(AppointmentTools.class);

    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private MailSenderService mailSenderService;
    @Autowired
    private AppUserService appUserService;
    @Autowired
    private ChatSessionUserBindingService chatSessionUserBindingService;

    @Tool(name="预约挂号", value = "用于最终创建预约记录。只有在姓名、身份证号、预约科室、预约日期、预约时间都已经明确，且用户已经明确确认要提交预约时才调用。工具会返回结构化字段：status、nextAction、userVisibleSummary、missingFields，以及当前预约详情。模型必须优先依据这些字段回复用户。")
    @Transactional(rollbackFor = Exception.class)
    public String bookAppointment(
            @ToolMemoryId Long memoryId,
            @P(value = "预约人姓名") String username,
            @P(value = "身份证号") String idCard,
            @P(value = "预约科室") String department,
            @P(value = "预约日期，格式示例：2026-04-02") String date,
            @P(value = "预约时间，可选值：上午、下午") String time,
            @P(value = "预约医生姓名", required = false) String doctorName
    ){
        Appointment appointment = buildAppointment(username, idCard, department, date, time, doctorName);
        Appointment normalized = normalizeAppointment(appointment);
        List<String> missingFields = requiredBookingFields(normalized);
        if (!missingFields.isEmpty()) {
            return structuredResult(
                    "BOOK_APPOINTMENT",
                    "MISSING_REQUIRED_FIELDS",
                    "预约信息不完整，请先补齐必填字段",
                    normalized,
                    missingFields
            );
        }
        try {
            Appointment appointmentDB = appointmentService.getOne(normalized);
            if(appointmentDB == null){
                normalized.setId(null);//防止大模型幻觉设置了id
                if(appointmentService.save(normalized)){
                    Appointment saved = appointmentService.getOne(normalized);
                    AppUser currentUserEntity = resolveCurrentUser(memoryId);
                    if (currentUserEntity != null && currentUserEntity.getEmail() != null) {
                        log.info("预约成功后发送邮件 memoryId={} userId={} email={}",
                                memoryId, currentUserEntity.getId(), currentUserEntity.getEmail());
                        mailSenderService.sendAppointSuccess(currentUserEntity.getEmail(), JSON.toJSONString(saved));
                    } else {
                        log.warn("预约成功但未发送邮件 memoryId={} reason=missing_current_user_or_email", memoryId);
                    }
                    return structuredResult(
                            "BOOK_APPOINTMENT",
                            "SUCCESS",
                            "预约已创建",
                            saved == null ? normalized : saved
                    );
                }else{
                    return structuredResult("BOOK_APPOINTMENT", "FAILED", "预约写入失败", normalized);
                }
            }
            return structuredResult("BOOK_APPOINTMENT", "DUPLICATE", "相同科室与时间已存在预约记录", appointmentDB);
        } catch (Exception exception) {
            log.error("预约挂号失败 appointment={}", normalized, exception);
            //throw exception;
            return structuredResult("BOOK_APPOINTMENT", "ERROR", "预约写入失败，请稍后重试 " + exception.getMessage(), normalized);
        }
    }

    @Tool(name="取消预约挂号", value = "用于最终取消已有预约。只有在姓名、身份证号、预约科室、预约日期、预约时间都已经明确，且用户明确表示要取消时才调用。工具会返回结构化字段：status、nextAction、userVisibleSummary、missingFields，以及当前预约详情。模型必须优先依据这些字段回复用户。")
    public String cancelAppointment(
            @ToolMemoryId Long memoryId,
            @P(value = "预约人姓名") String username,
            @P(value = "身份证号") String idCard,
            @P(value = "预约科室") String department,
            @P(value = "预约日期，格式示例：2026-04-02") String date,
            @P(value = "预约时间，可选值：上午、下午") String time,
            @P(value = "预约医生姓名", required = false) String doctorName
    ){
        Appointment appointment = buildAppointment(username, idCard, department, date, time, doctorName);
        Appointment normalized = normalizeAppointment(appointment);
        List<String> missingFields = requiredBookingFields(normalized);
        if (!missingFields.isEmpty()) {
            return structuredResult(
                    "CANCEL_APPOINTMENT",
                    "MISSING_REQUIRED_FIELDS",
                    "取消预约所需信息不完整，请先补齐必填字段",
                    normalized,
                    missingFields
            );
        }
        try {
            Appointment appointmentDB = appointmentService.getOne(normalized);
            if(appointmentDB != null){
                //删除预约记录
                if(appointmentService.removeById(appointmentDB.getId())){
                    return structuredResult("CANCEL_APPOINTMENT", "SUCCESS", "预约已取消", appointmentDB);
                }else{
                    return structuredResult("CANCEL_APPOINTMENT", "FAILED", "预约取消失败", appointmentDB);
                }
            }
            return structuredResult("CANCEL_APPOINTMENT", "NOT_FOUND", "未查询到匹配的预约记录", normalized);
        } catch (Exception exception) {
            log.error("取消预约失败 appointment={}", normalized, exception);
            return structuredResult("CANCEL_APPOINTMENT", "ERROR", "取消预约失败，请稍后重试", normalized);
        }
    }

    @Tool(name = "查询是否有号源", value="用于快速判断某个科室、日期、时间、医生是否存在可预约号源。适合回答“有没有号”“能不能挂”这类问题。")
    public boolean queryDepartment(
            @P(value = "科室名称") String name,
            @P(value = "日期") String date,
            @P(value = "时间，可选值：上午、下午") String time,
            @P(value = "医生名称", required = false) String doctorName
    ) {
        //TODO 维护医生的排班信息：
        //如果没有指定医生名字，则根据其他条件查询是否有可以预约的医生（有返回true，否则返回false）；
        //如果指定了医生名字，则判断医生是否有排班（没有排班返回false）
        //如果有排班，则判断医生排班时间段是否已约满（约满返回false，有空闲时间返回true）

        return true;
    }

    @Tool(name = "查询号源详情", value = "用于处理“有没有号”“某天某科某医生能不能挂”这类问题。会返回结构化字段：status、nextAction、userVisibleSummary、department、date、time、doctorName。模型必须优先依据这些字段直接回答用户。")
    public String queryDepartmentDetail(
            @P(value = "科室名称") String name,
            @P(value = "日期") String date,
            @P(value = "时间，可选值：上午、下午") String time,
            @P(value = "医生名称", required = false) String doctorName
    ) {
        boolean available = queryDepartment(name, date, time, doctorName);
        List<String> lines = new ArrayList<>();
        lines.add("resultVersion=APPOINTMENT_TOOL_V2");
        lines.add("tool=QUERY_APPOINTMENT_SLOT");
        lines.add("status=" + (available ? "AVAILABLE" : "UNAVAILABLE"));
        lines.add("message=" + (available ? "当前条件下存在可预约号源" : "当前条件下暂无可预约号源"));
        lines.add("nextAction=INFORM_SLOT_RESULT");
        lines.add("userVisibleSummary=" + (available
                ? "当前条件下存在可预约号源，请直接告诉用户可以继续预约。"
                : "当前条件下暂无可预约号源，请直接告诉用户当前不可预约。"));
        lines.add("department=" + safe(name));
        lines.add("date=" + safe(date));
        lines.add("time=" + safe(time));
        lines.add("doctorName=" + safe(doctorName));
        return String.join("\n", lines);
    }

    private Appointment buildAppointment(String username,
                                         String idCard,
                                         String department,
                                         String date,
                                         String time,
                                         String doctorName) {
        Appointment appointment = new Appointment();
        appointment.setUsername(username);
        appointment.setIdCard(idCard);
        appointment.setDepartment(department);
        appointment.setDate(date);
        appointment.setTime(time);
        appointment.setDoctorName(doctorName);
        return appointment;
    }

    private Appointment normalizeAppointment(Appointment appointment) {
        Appointment normalized = appointment == null ? new Appointment() : appointment;
        normalized.setUsername(trimToNull(normalized.getUsername()));
        normalized.setIdCard(trimToNull(normalized.getIdCard()));
        normalized.setDepartment(trimToNull(normalized.getDepartment()));
        normalized.setDate(trimToNull(normalized.getDate()));
        normalized.setTime(trimToNull(normalized.getTime()));
        normalized.setDoctorName(trimToNull(normalized.getDoctorName()));
        return normalized;
    }

    private String structuredResult(String toolName, String status, String message, Appointment appointment) {
        return structuredResult(toolName, status, message, appointment, List.of());
    }

    private String structuredResult(String toolName, String status, String message, Appointment appointment, List<String> missingFields) {
        List<String> lines = new ArrayList<>();
        lines.add("resultVersion=APPOINTMENT_TOOL_V2");
        lines.add("tool=" + toolName);
        lines.add("status=" + status);
        lines.add("message=" + message);
        lines.add("nextAction=" + resolveNextAction(status));
        lines.add("userVisibleSummary=" + resolveUserVisibleSummary(status, message, appointment, missingFields));
        if (missingFields != null && !missingFields.isEmpty()) {
            lines.add("missingFields=" + String.join(",", missingFields));
        }
        if (appointment != null) {
            lines.add("appointmentId=" + safe(appointment.getId()));
            lines.add("username=" + safe(appointment.getUsername()));
            lines.add("idCard=" + safe(appointment.getIdCard()));
            lines.add("department=" + safe(appointment.getDepartment()));
            lines.add("date=" + safe(appointment.getDate()));
            lines.add("time=" + safe(appointment.getTime()));
            lines.add("doctorName=" + safe(appointment.getDoctorName()));
        }
        return String.join("\n", lines);
    }

    private String resolveNextAction(String status) {
        if ("MISSING_REQUIRED_FIELDS".equals(status)) {
            return "ASK_MISSING_FIELDS";
        }
        if ("SUCCESS".equals(status)) {
            return "CONFIRM_SUCCESS";
        }
        if ("DUPLICATE".equals(status)) {
            return "INFORM_DUPLICATE";
        }
        if ("NOT_FOUND".equals(status)) {
            return "INFORM_NOT_FOUND";
        }
        if ("FAILED".equals(status) || "ERROR".equals(status)) {
            return "INFORM_FAILURE";
        }
        if ("AVAILABLE".equals(status) || "UNAVAILABLE".equals(status)) {
            return "INFORM_SLOT_RESULT";
        }
        return "RESPOND_TO_USER";
    }

    private String resolveUserVisibleSummary(String status, String message, Appointment appointment, List<String> missingFields) {
        if ("MISSING_REQUIRED_FIELDS".equals(status)) {
            return "信息还不完整，请只补充缺失字段：" + String.join("、", missingFields == null ? List.of() : missingFields);
        }
        if ("SUCCESS".equals(status) && appointment != null) {
            return "预约已成功创建，请直接向用户确认预约成功，并简要复述预约信息。";
        }
        if ("DUPLICATE".equals(status)) {
            return "存在相同科室与时间的预约记录，请直接告知用户，不要重复创建。";
        }
        if ("NOT_FOUND".equals(status)) {
            return "未找到匹配的预约记录，请直接告知用户。";
        }
        if ("FAILED".equals(status) || "ERROR".equals(status)) {
            return "本次预约操作失败，请直接告知用户稍后重试。";
        }
        return message == null ? "" : message;
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> requiredBookingFields(Appointment appointment) {
        List<String> missing = new ArrayList<>();
        if (appointment == null) {
            missing.add("username");
            missing.add("idCard");
            missing.add("department");
            missing.add("date");
            missing.add("time");
            return missing;
        }
        if (appointment.getUsername() == null) {
            missing.add("username");
        }
        if (appointment.getIdCard() == null) {
            missing.add("idCard");
        }
        if (appointment.getDepartment() == null) {
            missing.add("department");
        }
        if (appointment.getDate() == null) {
            missing.add("date");
        }
        if (appointment.getTime() == null) {
            missing.add("time");
        }
        return missing;
    }

    private AppUser resolveCurrentUser(Long memoryId) {
        Long userId = chatSessionUserBindingService.resolveUserId(memoryId);
        if (userId == null) {
            log.warn("预约工具未找到会话绑定用户 memoryId={}", memoryId);
            return null;
        }
        AppUser user = appUserService.findById(userId);
        if (user == null) {
            log.warn("预约工具未找到登录用户实体 memoryId={} userId={}", memoryId, userId);
        } else {
            log.info("预约工具解析到当前登录用户 memoryId={} userId={} email={}",
                    memoryId, user.getId(), user.getEmail());
        }
        return user;
    }
}
