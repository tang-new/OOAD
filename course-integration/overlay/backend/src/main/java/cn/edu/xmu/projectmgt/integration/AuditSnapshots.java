package cn.edu.xmu.projectmgt.integration;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Captures real pre-change state only after the central operation gate has passed. */
@Component @Profile("integration")
public class AuditSnapshots {
    public static final String ATTRIBUTE="course.audit.snapshot";
    public record Snapshot(String type,String objectType,String objectId,Map<String,Object> before,Map<String,Object> context) {}
    private final JdbcTemplate jdbc;
    public AuditSnapshots(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    private Map<String,Object> one(String sql,Object... args) {
        var rows=jdbc.queryForList(sql,args); return rows.isEmpty()?Map.of():rows.getFirst();
    }
    private void context(Map<String,Object> out,Map<String,Object> row) {
        for(String key:List.of("termId","courseId","classId")) {
            Object value=row.get(key);if(value!=null)out.put(key,"cpm-"+key.substring(0,key.length()-2)+"-"+value);
        }
    }
    @SuppressWarnings("unchecked")
    public void prepare(HttpServletRequest request,String operation) {
        if(Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())) return;
        var vars=(Map<String,String>)request.getAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if(vars==null)vars=Map.of();
        String type=null,objectType=null,id=null; Map<String,Object> before=null;var ctx=new LinkedHashMap<String,Object>();
        if(operation.equals("updateOralRecord") || operation.equals("createGroupOralRecord") || operation.equals("createStudentOralRecord")) {
            type="score_changed";objectType="oral_record";id=vars.get("oralRecordId");
            if(id!=null) {
                var row=one("select a.id, a.verdict as status, a.group_score as score, m.course_id as courseId,c.term_id as termId,g.responsible_class_id as classId from oral_assessment a join milestone m on m.id=a.milestone_id join course c on c.id=m.course_id left join project_group g on g.id=a.group_id where a.id=?",id);
                context(ctx,row);before=new LinkedHashMap<>();before.put("status",row.get("status"));before.put("score",row.get("score"));
                before.put("members",jdbc.queryForList("select student_id as studentId,score from oral_individual_score where assessment_id=? order by student_id",id));
            } else {
                context(ctx,one("select m.course_id as courseId,c.term_id as termId from milestone m join course c on c.id=m.course_id where m.id=?",vars.get("milestoneId")));
            }
        } else if(operation.toLowerCase().contains("group") && !operation.contains("GroupRule")) {
            type="group_changed";objectType="group";id=vars.get("groupId");
            if(id==null && vars.containsKey("requestId")) {
                var row=one("select group_id as groupId,state as status from group_join_request where id=?",vars.get("requestId"));
                if(row.get("groupId")!=null)id=row.get("groupId").toString();before=new LinkedHashMap<>();before.put("status",row.get("status"));
            }
            if(id!=null)context(ctx,one("select g.course_id as courseId,g.responsible_class_id as classId,c.term_id as termId from project_group g join course c on c.id=g.course_id where g.id=?",id));
            else if(vars.get("courseId")!=null)context(ctx,one("select id as courseId,term_id as termId from course where id=?",vars.get("courseId")));
        } else if(operation.equals("createMilestone") || operation.equals("updateMilestone") || operation.equals("deleteMilestone")) {
            type="milestone_changed";objectType="milestone";id=vars.get("milestoneId");
            if(id!=null)context(ctx,one("select m.course_id as courseId,c.term_id as termId from milestone m join course c on c.id=m.course_id where m.id=?",id));
            else context(ctx,one("select id as courseId,term_id as termId from course where id=?",vars.get("courseId")));
        } else if(Set.of("addStudent","removeStudent","importRoster").contains(operation)) {
            type="roster_changed";objectType="class";id=vars.get("classId");
            context(ctx,one("select t.id as classId,t.course_id as courseId,c.term_id as termId from teaching_class t join course c on c.id=t.course_id where t.id=?",id));
        }
        if(type!=null)request.setAttribute(ATTRIBUTE,new Snapshot(type,objectType,id,before,ctx));
    }
    /** Minimized audit representation, matching the central service's safe-field schema. */
    public static Object safe(Object value) {
        if(value instanceof Map<?,?> map) {
            var out=new LinkedHashMap<String,Object>();
            for(var e:map.entrySet()) {
                String key=e.getKey().toString();
                key=switch(key) {case "verdict","state"->"status";case "groupScore"->"score";case "personalScores"->"members";default->key;};
                if(Set.of("id","code","name","status","score","studentId","teacherId","groupId","classId","courseId","termId","milestoneId","oralRecordId","members","count").contains(key)) out.put(key,safe(e.getValue()));
            }
            return out;
        }
        if(value instanceof List<?> list)return list.stream().map(AuditSnapshots::safe).toList();
        return value;
    }
}
