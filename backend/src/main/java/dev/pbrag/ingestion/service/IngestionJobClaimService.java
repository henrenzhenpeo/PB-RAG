package dev.pbrag.ingestion.service;

import dev.pbrag.ingestion.domain.IngestionJob;
import dev.pbrag.ingestion.domain.IngestionJobAction;
import dev.pbrag.ingestion.domain.IngestionJobState;
import dev.pbrag.ingestion.mapper.IngestionJobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将过期恢复、重试唤醒和并发任务领取封装在同一个短事务中。 */
@Service
public class IngestionJobClaimService {

	private final IngestionJobMapper jobMapper;

	/** 注入负责租约恢复和并发领取的任务 Mapper。 */
	public IngestionJobClaimService(IngestionJobMapper jobMapper) {
		this.jobMapper = jobMapper;
	}

	/** 领取顺序固定为恢复过期任务、激活到期重试、使用 SKIP LOCKED 领取一个任务。 */
	@Transactional
	/** 在单个事务中恢复过期任务、释放到期重试并领取一个新任务。 */
	public IngestionJob recoverAndClaim(String workerId, long leaseSeconds, long retryDelaySeconds) {
		jobMapper.recoverExpired(retryDelaySeconds);
		jobMapper.makeRetriesAvailable();
		IngestionJob claimed = jobMapper.claimNext(workerId, leaseSeconds);
		if (claimed != null) {
			IngestionJobState expected = IngestionJobState.pending().apply(IngestionJobAction.START_PARSING);
			IngestionJobState actual = new IngestionJobState(claimed.status(), claimed.stage());
			if (!expected.equals(actual)) {
				throw new IllegalStateException("Claimed job did not enter the domain START_PARSING state");
			}
		}
		return claimed;
	}
}
