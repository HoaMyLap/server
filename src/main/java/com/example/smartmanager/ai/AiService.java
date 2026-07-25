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
            logsBuilder.append("- Không có hoạt động mới nào.\n");
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

        // Tạo prompt yêu cầu cấu trúc phân tích song song lồng ghép biểu đồ dạng JSON
        String prompt = String.format(
                "Bạn là một quản lý dự án công nghệ chuyên nghiệp. Hãy đọc danh sách các công việc hiện tại và nhật ký hoạt động dưới đây để viết báo cáo tiến độ dự án dưới dạng phân tích song song kèm biểu đồ.\n\n" +
                "%s\n\n" +
                "%s\n\n" +
                "Yêu cầu QUAN TRỌNG về định dạng JSON:\n" +
                "1. Trả về DUY NHẤT một mảng JSON hợp lệ bắt đầu bằng [ và kết thúc bằng ]. KHÔNG có bất kỳ văn bản, giải thích hay code block nào bên ngoài.\n" +
                "2. MỌI giá trị chuỗi (string) TUYỆT ĐỐI KHÔNG được chứa dấu ngoặc kép (\") bên trong. Thay bằng dấu nháy đơn (') nếu cần trích dẫn.\n" +
                "3. MỌI giá trị chuỗi phải nằm TRÊN MỘT DÒNG DUY NHẤT, không xuống dòng, không có ký tự \\n thô bên trong chuỗi.\n" +
                "4. Trường 'content' và 'insight' phải là một đoạn văn liên tục, không xuống dòng, không dùng ký tự đặc biệt markdown như **, ##, --.\n\n" +
                "Mỗi mục báo cáo trong mảng phải có cấu trúc chính xác như sau:\n" +
                "{\n" +
                "  \"title\": \"Tiêu đề mục phân tích\",\n" +
                "  \"content\": \"Văn bản phân tích xu hướng chi tiết viết liên tục trên một dòng không xuống dòng không dùng ngoặc kép\",\n" +
                "  \"chart\": {\n" +
                "    \"title\": \"Tên biểu đồ\",\n" +
                "    \"chartType\": \"doughnut\" | \"pie\" | \"line\" | \"horizontalBar\" | \"stackedBar\" | \"radar\" | \"bar\",\n" +
                "    \"labels\": [\"Nhãn 1\", \"Nhãn 2\"],\n" +
                "    \"datasets\": [\n" +
                "      {\n" +
                "        \"label\": \"Tên tập dữ liệu\",\n" +
                "        \"data\": [10, 20]\n" +
                "      }\n" +
                "    ],\n" +
                "    \"insight\": \"Nhận xét ngắn gọn viết liên tục trên một dòng không dùng ngoặc kép\"\n" +
                "  }\n" +
                "}\n\n" +
                "Nếu một mục không cần biểu đồ, đặt \"chart\": null.\n\n" +
                "Lồng ghép biểu đồ thống kê thực tế vào các mục phù hợp:\n" +
                "1. Completion Rate (doughnut)\n" +
                "2. Task Status (pie)\n" +
                "3. Tasks by Day (line)\n" +
                "4. Tasks by Member (horizontalBar)\n" +
                "5. Bug Severity (stackedBar)\n" +
                "6. Progress Timeline (line)\n" +
                "7. Sprint Burndown (line)\n" +
                "8. Team Productivity (radar)\n" +
                "9. Workload Distribution (bar)\n\n" +
                "Tính toán số liệu thống kê thực tế chính xác dựa trên dữ liệu dự án đã cung cấp ở trên.",
                tasksBuilder.toString(),
                logsBuilder.toString()
        );

        return openRouterServiceClient.generateContent(prompt);
    }
}
