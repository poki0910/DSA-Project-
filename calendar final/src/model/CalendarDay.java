package model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Model đại diện cho một ngày trong lịch, bao gồm danh sách các sự kiện.
 */
public class CalendarDay {
    private final LocalDate date;
    private final List<Event> events;
    private boolean isToday;
    private boolean isWeekend;

    public CalendarDay(LocalDate date) {
        this.date = date;
        this.events = new ArrayList<>();
        this.isToday = date.equals(LocalDate.now());
        int dow = date.getDayOfWeek().getValue(); // 1=T2 ... 7=CN
        this.isWeekend = (dow == 6 || dow == 7);
    }

    public LocalDate getDate() { return date; }
    public List<Event> getEvents() { return Collections.unmodifiableList(events); }
    public boolean isToday() { return isToday; }
    public boolean isWeekend() { return isWeekend; }
    public int getDayOfMonth() { return date.getDayOfMonth(); }

    public void addEvent(Event event) { events.add(event); }
    public void removeEvent(Event event) { events.remove(event); }

    public boolean hasEvents() { return !events.isEmpty(); }
    public int getEventCount() { return events.size(); }
}
