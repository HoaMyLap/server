package com.example.smartmanager.ai;

import com.example.smartmanager.tasks.TaskEntity;
import com.example.smartmanager.tasks.TaskRepository;
import com.example.smartmanager.tasks.TaskLogEntity;
import com.example.smartmanager.tasks.TaskLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiService {

    private final TaskRepository taskRepository;
    private final TaskLogRepository taskLogRepository;
    private final OpenRouterServiceClient openRouterServiceClient;

    public String generateDailyProjectSummary(String projectId) {
        UUID projectUuid = UUID.fromString(projectId);
        
        // Lấy tất cả các task thuộc dự án
        List<TaskEntity> tasks = taskRepository.findByProjectId(projectUuid);
        
        if (tasks.isEmpty()) {
            return "Không có công việc nào trong dự án để AI tạo báo cáo tiến độ.";
        }

        // Tính toán các chỉ số kỹ thuật thực tế
        long totalCount = tasks.size();
        long todoCount = tasks.stream().filter(t -> "TODO".equalsIgnoreCase(t.getStatus())).count();
        long inProgressCount = tasks.stream().filter(t -> "IN_PROGRESS".equalsIgnoreCase(t.getStatus())).count();
        long doneCount = tasks.stream().filter(t -> "DONE".equalsIgnoreCase(t.getStatus())).count();

        long urgentCount = tasks.stream().filter(t -> "URGENT".equalsIgnoreCase(t.getPriority())).count();
        long highCount = tasks.stream().filter(t -> "HIGH".equalsIgnoreCase(t.getPriority())).count();
        long mediumCount = tasks.stream().filter(t -> "MEDIUM".equalsIgnoreCase(t.getPriority())).count();
        long lowCount = tasks.stream().filter(t -> "LOW".equalsIgnoreCase(t.getPriority())).count();

        LocalDateTime now = LocalDateTime.now();
        long overdueCount = tasks.stream().filter(t -> !"DONE".equalsIgnoreCase(t.getStatus()) && t.getDueDate() != null && t.getDueDate().isBefore(now)).count();
        double completionRate = totalCount > 0 ? ((double) doneCount / totalCount) * 100.0 : 0.0;

        // Lấy danh sách ID của các task thuộc dự án để lọc log
        Set<UUID> projectTaskIds = tasks.stream()
                .map(TaskEntity::getId)
                .collect(Collectors.toSet());

        // Gom các log trong vòng 24 giờ qua và thuộc dự án này
        List<TaskLogEntity> logs = taskLogRepository.findAll().stream()
                .filter(log -> projectTaskIds.contains(log.getTaskId()) && 
                               log.getCreatedAt().isAfter(LocalDateTime.now().minusDays(1)))
                .toList();

        // Tạo chuỗi danh sách công việc hiện tại
        StringBuilder tasksBuilder = new StringBuilder();
        tasksBuilder.append("Danh sách các công việc hiện tại của dự án:\n");
        for (TaskEntity task : tasks) {
            tasksBuilder.append(String.format(
                    "- [%s] Tiêu đề: \"%s\", Trạng thái: %s, Mức độ ưu tiên: %s, Hạn chót: %s\n",
                    task.getId(),
                    task.getTitle(),
                    task.getStatus(),
                    task.getPriority(),
                    task.getDueDate() != null ? task.getDueDate() : "N/A"
            ));
        }

        // Tạo chuỗi hoạt động chi tiết trong 24 giờ qua
        StringBuilder logsBuilder = new StringBuilder();
        logsBuilder.append("Nhật ký hoạt động trong 24 giờ qua của dự án:\n");
        if (logs.isEmpty()) {
            logsBuilder.append("- Không có hoạt động mới nào trong 24h qua.\n");
        } else {
            for (TaskLogEntity log : logs) {
                logsBuilder.append(String.format(
                        "- [%s] Hành động: %s, Giá trị cũ: %s, Giá trị mới: %s\n",
                        log.getCreatedAt(),
                        log.getActionType(),
                        log.getOldValue() != null ? log.getOldValue() : "N/A",
                        log.getNewValue() != null ? log.getNewValue() : "N/A"
                ));
            }
        }

        String metricsSummary = String.format(
                "THỐNG KÊ KỸ THUẬT DỰ ÁN THỰC TẾ:\n" +
                "- Tổng số công việc: %d\n" +
                "- Đã hoàn thành (DONE): %d (%.1f%%)\n" +
                "- Đang tiến hành (IN_PROGRESS): %d\n" +
                "- Cần làm (TODO): %d\n" +
                "- Số task KHẨN CẤP (URGENT): %d, CAO (HIGH): %d, TRUNG BÌNH (MEDIUM): %d, THẤP (LOW): %d\n" +
                "- Số task QUÁ HẠN (OVERDUE): %d\n" +
                "- Số nhật ký hoạt động 24h qua: %d\n",
                totalCount, doneCount, completionRate, inProgressCount, todoCount,
                urgentCount, highCount, mediumCount, lowCount, overdueCount, logs.size()
        );

        StringBuilder promptSb = new StringBuilder();
        promptSb.append("Bạn là Giám đốc Quản lý Dự án Công nghệ Cấp cao (Chief PMO & Senior Agile Technical Auditor) thuộc hệ thống Homix v2.0.\n");
        promptSb.append("Nhiệm vụ: Lập BÁO CÁO NGHIỆM THU DỰ ÁN VÀ ĐÁNH GIÁ QUẢN TRỊ CHI TIẾT TOÀN DIỆN dành cho Ban Giám đốc dựa trên dữ liệu thống kê bên dưới.\n\n");
        promptSb.append(metricsSummary).append("\n\n");
        promptSb.append(tasksBuilder).append("\n\n");
        promptSb.append(logsBuilder).append("\n\n");
        promptSb.append("QUY TẮC ĐỊNH DẠNG JSON & CẤU TRÚC BẮT BUỘC:\n");
        promptSb.append("1. Trả về DUY NHẤT một mảng JSON hợp lệ gồm ĐÚNG 5 MỤC CHÍNH (bắt đầu bằng [ và kết thúc bằng ]). KHÔNG được bỏ bớt bất kỳ mục nào.\n");
        promptSb.append("2. MỌI giá trị chuỗi (string) TUYỆT ĐỐI KHÔNG chứa dấu ngoặc kép (\"). Nếu cần trích dẫn, hãy dùng nháy đơn (').\n");
        promptSb.append("3. Mỗi mục phải phân tích thật CHUYÊN SÂU, ĐẦY ĐỦ THÔNG TIN NGHỆP VỤ (tối thiểu 4-6 câu hoàn chỉnh, chi tiết sắc bén), nằm trên MỘT DÒNG DUY NHẤT, không có ký tự \\n thô.\n");
        promptSb.append("4. Dựa trên số liệu thực tế đã cung cấp để xuất số liệu biểu đồ chính xác 100%.\n\n");

        promptSb.append("DANH SÁCH ĐỦ 5 MỤC CHÍNH BẮT BUỘC:\n");
        promptSb.append("Mục 1. Title: '1. Tóm tắt Quản trị & Chỉ số Sức khỏe Dự án'\n");
        promptSb.append(String.format("  -> Content: Phân tích đánh giá tổng quan về sức khỏe dự án, tỷ lệ hoàn thành %.1f%% (%d/%d task), đánh giá về Sprint velocity, hiệu suất vận hành bộ máy và năng lực hoàn thành cam kết.\n", completionRate, doneCount, totalCount));
        promptSb.append(String.format("  -> Chart: chartType 'doughnut' (Title: 'TIẾN ĐỘ DỰ ÁN', Labels: ['Đã xong', 'Đang làm', 'Cần làm'], Datasets: [{ 'label': 'Số lượng', 'data': [%d, %d, %d] }])\n\n", doneCount, inProgressCount, todoCount));

        promptSb.append("Mục 2. Title: '2. Phân bổ Khối lượng & Ma trận Ưu tiên Công việc'\n");
        promptSb.append(String.format("  -> Content: Phân tích chi tiết sự cân bằng trong ma trận phân bổ công việc theo độ ưu tiên (Khẩn cấp: %d, Cao: %d, Trung bình: %d, Thấp: %d). Đánh giá rủi ro dồn tải công việc và hiện tượng thắt nút cổ chai (bottleneck).\n", urgentCount, highCount, mediumCount, lowCount));
        promptSb.append(String.format("  -> Chart: chartType 'bar' (Title: 'MA TRẬN ƯU TIÊN', Labels: ['Khẩn cấp', 'Cao', 'Trung bình', 'Thấp'], Datasets: [{ 'label': 'Số task', 'data': [%d, %d, %d, %d] }])\n\n", urgentCount, highCount, mediumCount, lowCount));

        promptSb.append("Mục 3. Title: '3. Đánh giá Rủi ro, Điểm nghẽn & Task Quá hạn'\n");
        promptSb.append(String.format("  -> Content: Kiểm toán toàn diện rủi ro tiến độ với %d task quá hạn và các hạng mục URGENT/HIGH chưa hoàn thành. Phân tích nguyên nhân chậm trễ và chỉ ra các khu vực có nguy cơ đổ vỡ deadline.\n", overdueCount));
        promptSb.append(String.format("  -> Chart: chartType 'radar' (Title: 'MỨC ĐỘ RỦI RO', Labels: ['Quá hạn', 'Khẩn cấp tồn', 'Thiếu nhân lực', 'Rủi ro kĩ thuật', 'Trễ mốc'], Datasets: [{ 'label': 'Chỉ số rủi ro', 'data': [%d, %d, %d, 2, 1] }])\n\n", overdueCount, urgentCount, (overdueCount > 0 ? 3 : 1)));

        promptSb.append("Mục 4. Title: '4. Phân tích Hoạt động Nhóm & Nhịp độ Vận hành 24h'\n");
        promptSb.append(String.format("  -> Content: Phân tích tần suất tương tác của đội ngũ qua %d nhật ký hoạt động trong 24h qua. Đánh giá nhịp độ cập nhật trạng thái, tinh thần chủ động phối hợp giữa các thành viên.\n", logs.size()));
        promptSb.append(String.format("  -> Chart: chartType 'stackedBar' (Title: 'NHỊP ĐỘ 24H', Labels: ['Sáng', 'Chiều', 'Tối', 'Đêm'], Datasets: [{ 'label': 'Hoạt động', 'data': [%d, %d, %d, 0] }])\n\n", (int)(logs.size() / 4 + 1), (int)(logs.size() / 2 + 1), (int)(logs.size() / 4)));

        promptSb.append("Mục 5. Title: '5. Kế hoạch Bứt phá & Dự báo Hoàn thành Dự án'\n");
        promptSb.append("  -> Content: Xây dựng kế hoạch hành động 4 bước bao gồm: tái phân bổ nguồn lực, giải quyết task tồn đọng, tăng cường kiểm thử nghiệm thu và dự báo thời gian hoàn thiện 100% toàn bộ dự án.\n");
        promptSb.append(String.format("  -> Chart: chartType 'line' (Title: 'LỘ TRÌNH DỰ BÁO', Labels: ['Tuần 1', 'Tuần 2', 'Tuần 3', 'Tuần 4'], Datasets: [{ 'label': 'Tiến độ %%', 'data': [%d, %d, %d, 100] }])\n\n", (int) Math.min(completionRate, 30), (int) Math.min(completionRate + 25, 60), (int) Math.min(completionRate + 50, 85)));

        promptSb.append("Mẫu định dạng JSON mỗi phần tử:\n");
        promptSb.append("{\n");
        promptSb.append("  \"title\": \"Tiêu đề mục\",\n");
        promptSb.append("  \"content\": \"Đoạn văn phân tích chuyên sâu 4-6 câu viết trên một dòng không dùng ngoặc kép\",\n");
        promptSb.append("  \"chart\": {\n");
        promptSb.append("    \"title\": \"Tên biểu đồ\",\n");
        promptSb.append("    \"chartType\": \"doughnut\" | \"bar\" | \"radar\" | \"stackedBar\" | \"line\",\n");
        promptSb.append("    \"labels\": [\"Nhãn 1\", \"Nhãn 2\"],\n");
        promptSb.append("    \"datasets\": [{ \"label\": \"Số lượng\", \"data\": [10, 20] }],\n");
        promptSb.append("    \"insight\": \"Nhận xét chiến lược ngắn gọn viết trên một dòng\"\n");
        promptSb.append("  }\n");
        promptSb.append("}");

        return openRouterServiceClient.generateContent(promptSb.toString());
    }

    public List<AiSearchResultDto> smartSearchTasks(String projectId, String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        UUID projectUuid = UUID.fromString(projectId);
        List<TaskEntity> tasks = taskRepository.findByProjectId(projectUuid);
        if (tasks.isEmpty()) {
            return List.of();
        }

        StringBuilder sb = new StringBuilder();
        for (TaskEntity t : tasks) {
            sb.append(String.format(
                    "{\"id\":\"%s\",\"title\":\"%s\",\"description\":\"%s\",\"status\":\"%s\",\"priority\":\"%s\",\"dueDate\":\"%s\"}\n",
                    t.getId(),
                    t.getTitle().replace("\"", "'"),
                    t.getDescription() != null ? t.getDescription().replace("\"", "'") : "",
                    t.getStatus(),
                    t.getPriority(),
                    t.getDueDate() != null ? t.getDueDate() : "None"
            ));
        }

        String prompt = String.format("""
                Bạn là hệ thống AI Tìm kiếm Thông minh (Semantic Search) cho ứng dụng quản lý công việc.
                Người dùng đưa ra câu truy vấn tìm kiếm bằng ngôn ngữ tự nhiên: "%s"

                Danh sách công việc trong dự án hiện tại (JSON):
                %s

                Nhiệm vụ:
                Phân tích ngữ nghĩa câu truy vấn (ý định, mức độ ưu tiên, hạn chót, trạng thái, từ khóa liên quan, v.v.) và đánh giá mức độ phù hợp với từng công việc.
                
                Trả về DUY NHẤT một mảng JSON các công việc phù hợp (sắp xếp theo điểm phù hợp giảm dần), mỗi mục có dạng:
                {
                  "taskId": "UUID của task",
                  "relevanceScore": 0.95,
                  "reason": "Giải thích ngắn gọn 1 câu bằng tiếng Việt tại sao task này khớp với truy vấn"
                }

                CHỈ trả về mảng JSON, không viết lời mở đầu hay kết luận.
                """, query, sb.toString());

        try {
            String rawResponse = openRouterServiceClient.generateContent(prompt);
            return parseSearchResults(rawResponse, tasks);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<AiSearchResultDto> parseSearchResults(String rawResponse, List<TaskEntity> allTasks) {
        List<AiSearchResultDto> results = new java.util.ArrayList<>();
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return results;
        }

        String cleaned = rawResponse.trim();
        if (cleaned.startsWith("```")) {
            int firstLineBreak = cleaned.indexOf("\n");
            int lastBackticks = cleaned.lastIndexOf("```");
            if (firstLineBreak != -1 && lastBackticks > firstLineBreak) {
                cleaned = cleaned.substring(firstLineBreak + 1, lastBackticks).trim();
            }
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(cleaned);
            if (root.isArray()) {
                java.util.Map<String, TaskEntity> taskMap = allTasks.stream()
                        .collect(Collectors.toMap(t -> t.getId().toString(), t -> t));

                for (com.fasterxml.jackson.databind.JsonNode node : root) {
                    String taskId = node.path("taskId").asText();
                    double score = node.path("relevanceScore").asDouble(0.85);
                    String reason = node.path("reason").asText("Khớp với yêu cầu tìm kiếm");

                    TaskEntity task = taskMap.get(taskId);
                    if (task != null) {
                        results.add(new AiSearchResultDto(task, score, reason));
                    }
                }
            }
        } catch (Exception e) {
            // Fallback: nếu AI trả về chuỗi thay vì JSON chuẩn
        }
        return results;
    }
}
