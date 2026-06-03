package repository;

import model.Event;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Repository lưu trữ các sự kiện (events) vào file CSV để dữ liệu được lưu giữa các lần chạy.
 *
 * Định dạng CSV: id,title,description,date,startTime,endTime,color
 */
public class EventRepository {
    private static final String DATA_FILE = "calendar_events.csv";
    private static final String CSV_HEADER = "id,title,description,date,startTime,endTime,color";
    private static final String SEPARATOR = ",";

    // Lưu trữ trong memory (cache)
    private final Map<String, Event> eventsById = new LinkedHashMap<>();

    public EventRepository() {
        loadFromFile();
    }

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    /**
     * Thêm một sự kiện mới.
     * @return true nếu thêm thành công
     */
    public boolean addEvent(Event event) {
        if (event == null || event.getId() == null) return false;
        eventsById.put(event.getId(), event);
        saveToFile();
        return true;
    }

    /**
     * Cập nhật thông tin sự kiện đã tồn tại (theo id).
     * @return true nếu tìm thấy và cập nhật thành công
     */
    public boolean updateEvent(Event updated) {
        if (updated == null || !eventsById.containsKey(updated.getId())) return false;
        eventsById.put(updated.getId(), updated);
        saveToFile();
        return true;
    }

    /**
     * Xóa sự kiện theo id.
     * @return true nếu xóa thành công
     */
    public boolean deleteEvent(String eventId) {
        if (!eventsById.containsKey(eventId)) return false;
        eventsById.remove(eventId);
        saveToFile();
        return true;
    }

    /**
     * Lấy sự kiện theo id.
     */
    public Optional<Event> findById(String eventId) {
        return Optional.ofNullable(eventsById.get(eventId));
    }

    // ─── QUERIES ──────────────────────────────────────────────────────────────

    /**
     * Lấy tất cả sự kiện trong một ngày cụ thể, sắp xếp theo giờ bắt đầu.
     */
    public List<Event> getEventsByDate(LocalDate date) {
        return eventsById.values().stream()
                .filter(e -> e.getDate().equals(date))
                .sorted(Comparator.comparing(Event::getStartTime))
                .collect(Collectors.toList());
    }

    /**
     * Lấy tất cả sự kiện trong một tháng.
     */
    public List<Event> getEventsByMonth(int year, int month) {
        return eventsById.values().stream()
                .filter(e -> e.getDate().getYear() == year && e.getDate().getMonthValue() == month)
                .sorted(Comparator.comparing(Event::getDate).thenComparing(Event::getStartTime))
                .collect(Collectors.toList());
    }

    /**
     * Lấy tất cả sự kiện trong khoảng ngày [from, to] (inclusive).
     */
    public List<Event> getEventsByDateRange(LocalDate from, LocalDate to) {
        return eventsById.values().stream()
                .filter(e -> !e.getDate().isBefore(from) && !e.getDate().isAfter(to))
                .sorted(Comparator.comparing(Event::getDate).thenComparing(Event::getStartTime))
                .collect(Collectors.toList());
    }

    /**
     * Tìm kiếm sự kiện theo tiêu đề (không phân biệt hoa thường).
     */
    public List<Event> searchByTitle(String keyword) {
        if (keyword == null || keyword.isBlank()) return Collections.emptyList();
        String lower = keyword.toLowerCase();
        return eventsById.values().stream()
                .filter(e -> e.getTitle().toLowerCase().contains(lower))
                .sorted(Comparator.comparing(Event::getDate))
                .collect(Collectors.toList());
    }

    /**
     * Lấy tất cả sự kiện.
     */
    public List<Event> getAllEvents() {
        return new ArrayList<>(eventsById.values());
    }

    /**
     * Trả về tập hợp các ngày có sự kiện trong tháng cho trước (dùng để highlight trên lịch).
     */
    public Set<LocalDate> getDatesWithEvents(int year, int month) {
        return eventsById.values().stream()
                .filter(e -> e.getDate().getYear() == year && e.getDate().getMonthValue() == month)
                .map(Event::getDate)
                .collect(Collectors.toSet());
    }

    // ─── PERSISTENCE ──────────────────────────────────────────────────────────

    private void saveToFile() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(DATA_FILE))) {
            pw.println(CSV_HEADER);
            for (Event e : eventsById.values()) {
                pw.println(toCsvLine(e));
            }
        } catch (IOException ex) {
            System.err.println("[EventRepository] Lỗi ghi file: " + ex.getMessage());
        }
    }

    private void loadFromFile() {
        Path path = Paths.get(DATA_FILE);
        if (!Files.exists(path)) return;

        try (BufferedReader br = new BufferedReader(new FileReader(DATA_FILE))) {
            String line = br.readLine(); // bỏ qua header
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                Event event = fromCsvLine(line);
                if (event != null) eventsById.put(event.getId(), event);
            }
        } catch (IOException ex) {
            System.err.println("[EventRepository] Lỗi đọc file: " + ex.getMessage());
        }
    }

    private String toCsvLine(Event e) {
        // Escape dấu phẩy trong chuỗi bằng cách bọc trong ngoặc kép
        return String.join(SEPARATOR,
                e.getId(),
                escapeCsv(e.getTitle()),
                escapeCsv(e.getDescription()),
                e.getDate().toString(),
                e.getStartTime().toString(),
                e.getEndTime().toString(),
                escapeCsv(e.getColor())
        );
    }

    private Event fromCsvLine(String line) {
        try {
            // Tách thủ công để xử lý quoted fields
            String[] parts = parseCsvLine(line);
            if (parts.length < 7) return null;

            String id          = parts[0].trim();
            String title       = unescapeCsv(parts[1]);
            String description = unescapeCsv(parts[2]);
            LocalDate date     = LocalDate.parse(parts[3].trim());
            LocalTime start    = LocalTime.parse(parts[4].trim());
            LocalTime end      = LocalTime.parse(parts[5].trim());
            String color       = unescapeCsv(parts[6]);

            Event event = new Event(title, description, date, start, end);
            // Dùng reflection để đặt lại id gốc (giữ UUID nhất quán)
            setPrivateId(event, id);
            event.setColor(color);
            return event;
        } catch (Exception ex) {
            System.err.println("[EventRepository] Dòng CSV không hợp lệ: " + line);
            return null;
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String unescapeCsv(String value) {
        if (value == null) return "";
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value;
    }

    private void setPrivateId(Event event, String id) {
        try {
            var field = Event.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(event, id);
        } catch (Exception ignored) {}
    }
}
