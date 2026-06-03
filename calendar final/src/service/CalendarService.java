package service;

import model.CalendarDay;
import model.Event;
import repository.EventRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Service layer chứa toàn bộ business logic của Calendar App.
 *
 * Chịu trách nhiệm:
 *  - Tạo dữ liệu lịch cho một tháng (dạng ma trận 6x7 như frontend cần)
 *  - Quản lý CRUD sự kiện
 *  - Kiểm tra xung đột giờ
 *  - Cung cấp dữ liệu highlight cho frontend
 */
public class CalendarService {

    private final EventRepository repository;

    public CalendarService() {
        this.repository = new EventRepository();
    }

    // Constructor để inject repository (giúp test)
    public CalendarService(EventRepository repository) {
        this.repository = repository;
    }

    // ─── CALENDAR GRID ────────────────────────────────────────────────────────

    /**
     * Tạo ma trận lịch tháng dạng [6][7] – khớp với CalendarFrontend.
     * Mỗi ô là một CalendarDay (có thể null nếu ô trống).
     *
     * @param yearMonth tháng cần tạo lịch
     * @return mảng 6 hàng x 7 cột, null = ô trống
     */
    public CalendarDay[][] buildMonthGrid(YearMonth yearMonth) {
        CalendarDay[][] grid = new CalendarDay[6][7];

        LocalDate firstDay = yearMonth.atDay(1);
        int startCol = firstDay.getDayOfWeek().getValue(); // T2=1 ... CN=7
        if (startCol == 7) startCol = 0; // CN → cột 0

        int daysInMonth = yearMonth.lengthOfMonth();
        Set<LocalDate> datesWithEvents = repository.getDatesWithEvents(
                yearMonth.getYear(), yearMonth.getMonthValue());

        int currentDay = 1;
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 7; col++) {
                if (row == 0 && col < startCol) {
                    grid[row][col] = null; // ô trống đầu tháng
                } else if (currentDay <= daysInMonth) {
                    LocalDate date = yearMonth.atDay(currentDay);
                    CalendarDay day = new CalendarDay(date);
                    // Gắn events vào ngày (dùng package-private addEvent)
                    if (datesWithEvents.contains(date)) {
                        List<Event> events = repository.getEventsByDate(date);
                        events.forEach(e -> addEventToDay(day, e));
                    }
                    grid[row][col] = day;
                    currentDay++;
                } else {
                    grid[row][col] = null; // ô trống cuối tháng
                }
            }
        }
        return grid;
    }

    /**
     * Tạo mảng Object[][] tương thích với DefaultTableModel của frontend.
     * Giá trị ô là số ngày (Integer) hoặc "" (chuỗi rỗng).
     */
    public Object[][] buildMonthGridForTable(YearMonth yearMonth) {
        CalendarDay[][] grid = buildMonthGrid(yearMonth);
        Object[][] table = new Object[6][7];
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 7; c++) {
                table[r][c] = (grid[r][c] != null) ? grid[r][c].getDayOfMonth() : "";
            }
        }
        return table;
    }

    /**
     * Lấy tập ngày có sự kiện trong tháng (dùng để highlight trên lịch).
     */
    public Set<LocalDate> getHighlightDates(YearMonth yearMonth) {
        return repository.getDatesWithEvents(yearMonth.getYear(), yearMonth.getMonthValue());
    }

    // ─── EVENT CRUD ───────────────────────────────────────────────────────────

    /**
     * Tạo và lưu sự kiện mới. Kiểm tra xung đột giờ trước khi lưu.
     *
     * @return Optional chứa Event nếu thành công, hoặc empty nếu có lỗi
     */
    public Optional<Event> createEvent(String title, String description,
                                       LocalDate date, LocalTime startTime, LocalTime endTime) {
        // Validate
        String error = validateEventInput(title, date, startTime, endTime);
        if (error != null) {
            System.err.println("[CalendarService] Validation lỗi: " + error);
            return Optional.empty();
        }

        // Kiểm tra xung đột
        if (hasConflict(null, date, startTime, endTime)) {
            System.err.println("[CalendarService] Xung đột lịch tại " + date + " " + startTime + "-" + endTime);
            return Optional.empty();
        }

        Event event = new Event(title.trim(), description == null ? "" : description.trim(),
                date, startTime, endTime);
        repository.addEvent(event);
        return Optional.of(event);
    }

    /**
     * Cập nhật sự kiện đã có.
     *
     * @return true nếu thành công
     */
    public boolean updateEvent(String eventId, String title, String description,
                               LocalDate date, LocalTime startTime, LocalTime endTime) {
        Optional<Event> existing = repository.findById(eventId);
        if (existing.isEmpty()) return false;

        String error = validateEventInput(title, date, startTime, endTime);
        if (error != null) return false;

        if (hasConflict(eventId, date, startTime, endTime)) return false;

        Event event = existing.get();
        event.setTitle(title.trim());
        event.setDescription(description == null ? "" : description.trim());
        event.setDate(date);
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        return repository.updateEvent(event);
    }

    /**
     * Xóa sự kiện theo id.
     */
    public boolean deleteEvent(String eventId) {
        return repository.deleteEvent(eventId);
    }

    /**
     * Lấy danh sách sự kiện trong một ngày.
     */
    public List<Event> getEventsForDate(LocalDate date) {
        return repository.getEventsByDate(date);
    }

    /**
     * Tìm kiếm sự kiện theo tiêu đề.
     */
    public List<Event> searchEvents(String keyword) {
        return repository.searchByTitle(keyword);
    }

    /**
     * Lấy tất cả sự kiện trong một tháng.
     */
    public List<Event> getEventsForMonth(YearMonth yearMonth) {
        return repository.getEventsByMonth(yearMonth.getYear(), yearMonth.getMonthValue());
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    /**
     * Kiểm tra xung đột giờ trong cùng ngày.
     * Sự kiện A xung đột B nếu khoảng thời gian chồng nhau.
     *
     * @param excludeId id của event đang được sửa (để không so sánh với chính nó), null nếu tạo mới
     */
    private boolean hasConflict(String excludeId, LocalDate date, LocalTime start, LocalTime end) {
        List<Event> existing = repository.getEventsByDate(date);
        for (Event e : existing) {
            if (excludeId != null && e.getId().equals(excludeId)) continue;
            // Xung đột: start1 < end2 && start2 < end1
            if (start.isBefore(e.getEndTime()) && e.getStartTime().isBefore(end)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validate dữ liệu đầu vào của event.
     *
     * @return chuỗi lỗi hoặc null nếu hợp lệ
     */
    private String validateEventInput(String title, LocalDate date, LocalTime start, LocalTime end) {
        if (title == null || title.isBlank()) return "Tiêu đề không được để trống";
        if (date == null) return "Ngày không được để trống";
        if (start == null || end == null) return "Giờ bắt đầu và kết thúc không được để trống";
        if (!start.isBefore(end)) return "Giờ bắt đầu phải trước giờ kết thúc";
        return null;
    }

    /** Truy cập package-private CalendarDay.addEvent qua cùng package */
    private void addEventToDay(CalendarDay day, Event event) {
        day.addEvent(event);
    }
}
