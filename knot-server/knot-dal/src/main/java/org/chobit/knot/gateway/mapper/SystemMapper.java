package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.BackupJobEntity;
import org.chobit.knot.gateway.entity.GatewayNodeEntity;
import org.chobit.knot.gateway.entity.OperationLogDetailEntity;
import org.chobit.knot.gateway.entity.OperationLogEntity;
import org.chobit.knot.gateway.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SystemMapper {

    List<UserEntity> listUsers();

    UserEntity getUserById(Long id);

    int insertUser(UserEntity entity);

    int updateUserStatus(UserEntity entity);

    List<OperationLogEntity> listOperationLogs();

    OperationLogDetailEntity getOperationLogDetail(Long logId);

    List<GatewayNodeEntity> listNodes();

    int insertBackupJob(BackupJobEntity entity);

    int updateBackupJobStatus(BackupJobEntity entity);
}
