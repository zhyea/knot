package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.BackupJobEntity;
import org.chobit.knot.gateway.entity.GatewayNodeEntity;
import org.chobit.knot.gateway.entity.OperationLogDetailEntity;
import org.chobit.knot.gateway.entity.OperationLogEntity;
import org.chobit.knot.gateway.entity.UserEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SystemMapper {
    @Select("select id, username, real_name, status from users order by id desc")
    List<UserEntity> listUsers();

    @Select("select id, username, real_name, status from users where id=#{id}")
    UserEntity getUserById(Long id);

    @Insert("insert into users(username,password_hash,real_name,status) values(#{username},'NO_LOGIN',#{realName},#{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(UserEntity entity);

    @Update("update users set status=#{status} where id=#{id}")
    int updateUserStatus(UserEntity entity);

    @Select("select id,module_code,action_code,target_id,result_status from operation_logs order by id desc")
    List<OperationLogEntity> listOperationLogs();

    @Select("select log_id,before_json,after_json from operation_log_details where log_id=#{logId}")
    OperationLogDetailEntity getOperationLogDetail(Long logId);

    @Select("select id,node_id,host,status from gateway_nodes order by id desc")
    List<GatewayNodeEntity> listNodes();

    @Insert("insert into backup_jobs(job_code,status,snapshot_ref) values(#{jobCode},#{status},#{snapshotRef})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertBackupJob(BackupJobEntity entity);

    @Update("update backup_jobs set status=#{status} where job_code=#{jobCode}")
    int updateBackupJobStatus(BackupJobEntity entity);
}
