import model.CalendarDay;
import model.Event;
import service.CalendarService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * CalendarFrontend – giao diện Swing kết nối với CalendarService (backend).
 *
 * Tính năng:
 *  - Xem lịch tháng, điều hướng tháng trước/sau
 *  - Highlight ngày hôm nay và ngày cuối tuần
 *  - Highlight ngày có sự kiện (dấu chấm xanh)
 *  - Click vào ngày → xem danh sách sự kiện
 *  - Thêm / Xóa sự kiện qua dialog
 */
public class CalendarFrontend extends JFrame {
    // ─── Backend ──────────────────────────────────────────────────────────────
    private final CalendarService calendarService = new CalendarService();

    // ─── UI Components ────────────────────────────────────────────────────────
    private JLabel monthYearLabel;
    private JTable calendarTable;
    private DefaultTableModel tableModel;
    private YearMonth currentYearMonth;

    // Dữ liệu lịch hiện tại
    private CalendarDay[][] currentGrid;
    private Set<LocalDate> highlightDates;

    // Renderer tùy chỉnh
    private CalendarCellRenderer cellRenderer;

    // ─── Constants ────────────────────────────────────────────────────────────
    private static final Color COLOR_TODAY     = new Color(255, 220, 100);
    private static final Color COLOR_WEEKEND   = new Color(255, 240, 240);
    private static final Color COLOR_HAS_EVENT = new Color(74, 144, 217);
    private static final Color COLOR_HEADER    = new Color(50, 50, 80);
    private static final Color COLOR_BG        = new Color(245, 247, 250);

    // ─── Constructor ──────────────────────────────────────────────────────────
    public CalendarFrontend() {
        setTitle("Java Calendar App");
        setSize(600, 480);
        setMinimumSize(new Dimension(500, 400));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 8));
        getContentPane().setBackground(COLOR_BG);

        currentYearMonth = YearMonth.now();
        buildUI();
        updateCalendar();
        setLocationRelativeTo(null);
    }

    // ─── UI Builder ───────────────────────────────────────────────────────────
    private void buildUI() {
        add(buildHeader(), BorderLayout.NORTH);
        add(buildCalendarTable(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(COLOR_HEADER);
        panel.setBorder(new EmptyBorder(10, 16, 10, 16));

        JButton prevBtn = makeNavButton("<< Trước");
        JButton nextBtn = makeNavButton("Sau >>");
        prevBtn.addActionListener(e -> changeMonth(-1));
        nextBtn.addActionListener(e -> changeMonth(1));

        monthYearLabel = new JLabel("", SwingConstants.CENTER);
        monthYearLabel.setFont(new Font("Arial", Font.BOLD, 18));
        monthYearLabel.setForeground(Color.WHITE);

        panel.add(prevBtn, BorderLayout.WEST);
        panel.add(monthYearLabel, BorderLayout.CENTER);
        panel.add(nextBtn, BorderLayout.EAST);
        return panel;
    }

    private JScrollPane buildCalendarTable() {
        String[] columns = {"CN", "T2", "T3", "T4", "T5", "T6", "T7"};
        tableModel = new DefaultTableModel(null, columns) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        calendarTable = new JTable(tableModel);
        calendarTable.setRowHeight(55);
        calendarTable.setGridColor(new Color(210, 215, 225));
        calendarTable.setShowGrid(true);
        calendarTable.setSelectionBackground(new Color(200, 220, 255));
        calendarTable.getTableHeader().setReorderingAllowed(false);
        calendarTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 13));
        calendarTable.getTableHeader().setBackground(new Color(220, 225, 235));
        calendarTable.getTableHeader().setForeground(Color.DARK_GRAY);

        cellRenderer = new CalendarCellRenderer();
        for (int c = 0; c < 7; c++) calendarTable.getColumnModel().getColumn(c).setCellRenderer(cellRenderer);

        // Click vào ô → xem/thêm sự kiện
        calendarTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) onDayClick(e);
            }
        });

        JScrollPane scrollPane = new JScrollPane(calendarTable);
        scrollPane.setBorder(new EmptyBorder(0, 8, 0, 8));
        return scrollPane;
    }

    private JPanel buildFooter() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBackground(COLOR_BG);
        panel.setBorder(new EmptyBorder(0, 0, 6, 10));

        JButton todayBtn = new JButton("Hôm nay");
        todayBtn.addActionListener(e -> {
            currentYearMonth = YearMonth.now();
            updateCalendar();
        });

        JButton searchBtn = new JButton("🔍 Tìm kiếm");
        searchBtn.addActionListener(e -> showSearchDialog());

        panel.add(todayBtn);
        panel.add(searchBtn);
        return panel;
    }

    private JButton makeNavButton(String text) {
        JButton btn = new JButton(text);
        btn.setForeground(Color.WHITE);
        btn.setBackground(COLOR_HEADER);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Arial", Font.BOLD, 13));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ─── Calendar Logic ───────────────────────────────────────────────────────
    private void changeMonth(int offset) {
        currentYearMonth = currentYearMonth.plusMonths(offset);
        updateCalendar();
    }

    private void updateCalendar() {
        // Lấy dữ liệu từ backend
        currentGrid    = calendarService.buildMonthGrid(currentYearMonth);
        highlightDates = calendarService.getHighlightDates(currentYearMonth);
        cellRenderer.setHighlightDates(highlightDates);

        String monthName = switch (currentYearMonth.getMonthValue()) {
            case 1  -> "Tháng 1"; case 2  -> "Tháng 2"; case 3  -> "Tháng 3";
            case 4  -> "Tháng 4"; case 5  -> "Tháng 5"; case 6  -> "Tháng 6";
            case 7  -> "Tháng 7"; case 8  -> "Tháng 8"; case 9  -> "Tháng 9";
            case 10 -> "Tháng 10"; case 11 -> "Tháng 11"; default -> "Tháng 12";
        };
        monthYearLabel.setText(monthName + " " + currentYearMonth.getYear());

        Object[][] tableData = calendarService.buildMonthGridForTable(currentYearMonth);
        tableModel.setRowCount(0);
        for (Object[] row : tableData) tableModel.addRow(row);
    }

    // ─── Event Handling ───────────────────────────────────────────────────────
    private void onDayClick(MouseEvent e) {
        int row = calendarTable.rowAtPoint(e.getPoint());
        int col = calendarTable.columnAtPoint(e.getPoint());
        if (row < 0 || col < 0 || currentGrid == null) return;

        CalendarDay day = currentGrid[row][col];
        if (day == null) return;

        showDayDialog(day.getDate());
    }

    private void showDayDialog(LocalDate date) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        JDialog dialog = new JDialog(this, "Ngày " + date.format(fmt), true);
        dialog.setSize(460, 380);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.setLocationRelativeTo(this);

        // Danh sách sự kiện
        List<Event> events = calendarService.getEventsForDate(date);
        DefaultListModel<String> listModel = new DefaultListModel<>();
        events.forEach(ev -> listModel.addElement(formatEventItem(ev)));

        JList<String> eventList = new JList<>(listModel);
        eventList.setFont(new Font("Arial", Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(eventList);
        scroll.setBorder(BorderFactory.createTitledBorder("Sự kiện trong ngày"));

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addBtn    = new JButton("+ Thêm");
        JButton deleteBtn = new JButton("Xóa");
        JButton closeBtn  = new JButton("Đóng");

        addBtn.addActionListener(e -> {
            dialog.dispose();
            showAddEventDialog(date, () -> {
                updateCalendar();
                showDayDialog(date);
            });
        });

        deleteBtn.addActionListener(e -> {
            int idx = eventList.getSelectedIndex();
            if (idx < 0) { JOptionPane.showMessageDialog(dialog, "Vui lòng chọn sự kiện cần xóa."); return; }
            Event ev = events.get(idx);
            int confirm = JOptionPane.showConfirmDialog(dialog,
                    "Xóa sự kiện \"" + ev.getTitle() + "\"?", "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                calendarService.deleteEvent(ev.getId());
                updateCalendar();
                dialog.dispose();
                showDayDialog(date);
            }
        });

        closeBtn.addActionListener(e -> dialog.dispose());

        btnPanel.add(addBtn);
        btnPanel.add(deleteBtn);
        btnPanel.add(closeBtn);

        dialog.add(scroll, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showAddEventDialog(LocalDate date, Runnable onSuccess) {
        JDialog dialog = new JDialog(this, "Thêm sự kiện mới", true);
        dialog.setSize(400, 300);
        dialog.setLayout(new GridBagLayout());
        dialog.setLocationRelativeTo(this);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 10, 6, 10);

        JTextField titleField  = new JTextField(20);
        JTextField descField   = new JTextField(20);
        JTextField startField  = new JTextField("09:00", 10);
        JTextField endField    = new JTextField("10:00", 10);

        addFormRow(dialog, gbc, 0, "Tiêu đề *:", titleField);
        addFormRow(dialog, gbc, 1, "Mô tả:", descField);
        addFormRow(dialog, gbc, 2, "Giờ bắt đầu (HH:mm):", startField);
        addFormRow(dialog, gbc, 3, "Giờ kết thúc (HH:mm):", endField);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn   = new JButton("Lưu");
        JButton cancelBtn = new JButton("Hủy");
        cancelBtn.addActionListener(e -> dialog.dispose());

        saveBtn.addActionListener(e -> {
            try {
                LocalTime start = LocalTime.parse(startField.getText().trim());
                LocalTime end   = LocalTime.parse(endField.getText().trim());
                Optional<Event> result = calendarService.createEvent(
                        titleField.getText(), descField.getText(), date, start, end);
                if (result.isPresent()) {
                    dialog.dispose();
                    onSuccess.run();
                } else {
                    JOptionPane.showMessageDialog(dialog,
                            "Không thể lưu sự kiện.\nKiểm tra lại tiêu đề, giờ, hoặc xung đột lịch.",
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog,
                        "Định dạng giờ không hợp lệ. Vui lòng nhập dạng HH:mm (vd: 09:30).",
                        "Lỗi định dạng", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        dialog.add(btnPanel, gbc);
        dialog.setVisible(true);
    }

    private void showSearchDialog() {
        String keyword = JOptionPane.showInputDialog(this, "Nhập từ khóa tìm kiếm:", "Tìm kiếm sự kiện", JOptionPane.PLAIN_MESSAGE);
        if (keyword == null || keyword.isBlank()) return;

        List<Event> results = calendarService.searchEvents(keyword);
        if (results.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Không tìm thấy sự kiện nào với từ khóa: \"" + keyword + "\"");
            return;
        }

        DefaultListModel<String> model = new DefaultListModel<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        results.forEach(ev -> model.addElement("[" + ev.getDate().format(fmt) + "] " + formatEventItem(ev)));

        JList<String> list = new JList<>(model);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(420, 200));
        JOptionPane.showMessageDialog(this, scroll, "Kết quả tìm kiếm (" + results.size() + ")", JOptionPane.PLAIN_MESSAGE);
    }

    // ─── Utilities ────────────────────────────────────────────────────────────
    private void addFormRow(JDialog dialog, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1; gbc.weightx = 0;
        dialog.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        dialog.add(field, gbc);
    }

    private String formatEventItem(Event ev) {
        return String.format("%s–%s  %s", ev.getStartTime(), ev.getEndTime(), ev.getTitle());
    }

    // ─── Custom Cell Renderer ─────────────────────────────────────────────────
    private class CalendarCellRenderer extends DefaultTableCellRenderer {
        private Set<LocalDate> highlightDates = Set.of();

        void setHighlightDates(Set<LocalDate> dates) { this.highlightDates = dates; }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                        boolean isSelected, boolean hasFocus, int row, int col) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setVerticalAlignment(SwingConstants.TOP);
            label.setFont(new Font("Arial", Font.PLAIN, 14));
            label.setBorder(new EmptyBorder(6, 4, 4, 4));

            if (value == null || value.toString().isBlank()) {
                label.setBackground(COLOR_BG);
                label.setText("");
                return label;
            }

            // Xác định ngày tương ứng
            int dayNum = (value instanceof Integer) ? (int) value : Integer.parseInt(value.toString());
            LocalDate cellDate = currentYearMonth.atDay(dayNum);

            // Màu nền
            if (isSelected) {
                label.setBackground(new Color(200, 220, 255));
            } else if (cellDate.equals(LocalDate.now())) {
                label.setBackground(COLOR_TODAY);
                label.setFont(new Font("Arial", Font.BOLD, 14));
            } else if (col == 0 || col == 6) { // CN hoặc T7
                label.setBackground(COLOR_WEEKEND);
            } else {
                label.setBackground(Color.WHITE);
            }

            // Nếu có sự kiện, thêm dấu chấm nhỏ vào text
            if (highlightDates.contains(cellDate)) {
                label.setText("<html><center>" + dayNum + "<br><font color='#4A90D9'>●</font></center></html>");
            }

            return label;
        }
    }

    // ─── Main ─────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> new CalendarFrontend().setVisible(true));
    }
}
