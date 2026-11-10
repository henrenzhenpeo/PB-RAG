package dev.pbrag.ingestion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.pbrag.ingestion.domain.IngestionJob;
import dev.pbrag.ingestion.domain.IngestionStage;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 入库任务持久层入口。普通写入复用 BaseMapper；状态迁移、租约恢复和
 * {@code SKIP LOCKED} 并发领取由 XML 中的条件 SQL 保证原子性。
 */
@Mapper
public interface IngestionJobMapper extends BaseMapper<IngestionJob> {

	/** 在 Workspace 作用域内按 ID 查询入库任务。 */
	IngestionJob selectByWorkspaceIdAndId(
			@Param("workspaceId") UUID workspaceId,
			@Param("id") UUID id);

	/** 查询指定文档版本关联的入库任务。 */
	IngestionJob selectByDocumentVersionId(@Param("documentVersionId") UUID documentVersionId);

	/** 使用 SKIP LOCKED 原子领取下一个可执行任务并建立租约。 */
	IngestionJob claimNext(
			@Param("workerId") String workerId,
			@Param("leaseSeconds") long leaseSeconds);

	/** 在任务阶段和租约均匹配时原子推进处理阶段。 */
	int advanceStage(
			@Param("id") UUID id,
			@Param("workerId") String workerId,
			@Param("expectedStage") IngestionStage expectedStage,
			@Param("nextStage") IngestionStage nextStage,
			@Param("leaseSeconds") long leaseSeconds);

	/** 延长当前 Worker 所持任务租约，防止长步骤被错误回收。 */
	int renewLease(
			@Param("id") UUID id,
			@Param("workerId") String workerId,
			@Param("stage") IngestionStage stage,
			@Param("leaseSeconds") long leaseSeconds);

	/** 将当前 Worker 持有的任务原子标记为完成。 */
	int complete(@Param("id") UUID id, @Param("workerId") String workerId);

	/** 根据尝试次数将失败任务转为延迟重试或最终失败。 */
	int failOrRetry(
			@Param("id") UUID id,
			@Param("workerId") String workerId,
			@Param("errorCode") String errorCode,
			@Param("errorMessage") String errorMessage,
			@Param("expectedStage") IngestionStage expectedStage,
			@Param("retryDelaySeconds") long retryDelaySeconds);

	/** 回收租约已过期的运行中任务并安排重试。 */
	int recoverExpired(@Param("retryDelaySeconds") long retryDelaySeconds);

	/** 将等待时间已到的重试任务恢复为可领取状态。 */
	int makeRetriesAvailable();
}
