package dev.pbrag.chat.mapper;

import dev.pbrag.chat.domain.ChatMessageRecord;
import dev.pbrag.chat.domain.ChatSessionRecord;
import dev.pbrag.chat.domain.RagCitation;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 会话、消息和版本级引用的持久层入口。 */
@Mapper
public interface ChatHistoryMapper {

	/** 创建归属于当前用户和知识库的会话。 */
	int insertSession(
			@Param("id") UUID id,
			@Param("workspaceId") UUID workspaceId,
			@Param("knowledgeBaseId") UUID knowledgeBaseId,
			@Param("userId") UUID userId,
			@Param("title") String title);

	/** 在用户、Workspace 和知识库作用域内读取会话。 */
	ChatSessionRecord selectSession(
			@Param("id") UUID id,
			@Param("workspaceId") UUID workspaceId,
			@Param("knowledgeBaseId") UUID knowledgeBaseId,
			@Param("userId") UUID userId);

	/** 写入一条用户或助手消息。 */
	int insertMessage(
			@Param("id") UUID id,
			@Param("workspaceId") UUID workspaceId,
			@Param("sessionId") UUID sessionId,
			@Param("turnId") UUID turnId,
			@Param("role") String role,
			@Param("status") String status,
			@Param("content") String content);

	/** 写入一条与助手消息绑定的引用快照。 */
	int insertCitation(
			@Param("id") UUID id,
			@Param("workspaceId") UUID workspaceId,
			@Param("messageId") UUID messageId,
			@Param("citation") RagCitation citation);

	/** 将助手消息更新为完成或中断终态，并保存已生成正文。 */
	int finishAssistant(
			@Param("workspaceId") UUID workspaceId,
			@Param("messageId") UUID messageId,
			@Param("status") String status,
			@Param("content") String content,
			@Param("errorCode") String errorCode);

	/** 刷新会话最后活动时间。 */
	int touchSession(@Param("workspaceId") UUID workspaceId, @Param("sessionId") UUID sessionId);

	/** 将服务重启后遗留的流式消息收敛为可恢复的中断终态。 */
	int interruptStaleStreaming(
			@Param("workspaceId") UUID workspaceId,
			@Param("sessionId") UUID sessionId);

	/** 按创建顺序加载会话中的全部消息。 */
	List<ChatMessageRecord> selectMessages(
			@Param("workspaceId") UUID workspaceId,
			@Param("sessionId") UUID sessionId);

	/** 加载一条助手消息的全部引用。 */
	List<RagCitation> selectCitations(
			@Param("workspaceId") UUID workspaceId,
			@Param("messageId") UUID messageId);
}
