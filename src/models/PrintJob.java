package models;

import interfaces.PaperDemand;

import java.io.Serializable;

public abstract class PrintJob implements PaperDemand, Serializable {
    private static final long serialVersionUID = 1L;

    private final int id;
    private final int ownerId;
    private final int pages;
    private final int copies;
    private final int priority;
    private boolean printed;

    protected PrintJob(int id, int ownerId, int pages, int copies, int priority) {
        if (id <= 0) throw new IllegalArgumentException("id должен быть положительным");
        if (ownerId <= 0) throw new IllegalArgumentException("ownerId должен быть положительным");
        if (pages < 1 || pages > 100) throw new IllegalArgumentException("pages: 1..100");
        if (copies < 1 || copies > 100) throw new IllegalArgumentException("copies: 1..100");
        if (priority < 1 || priority > 5) throw new IllegalArgumentException("priority: 1..5");
        this.id = id;
        this.ownerId = ownerId;
        this.pages = pages;
        this.copies = copies;
        this.priority = priority;
        this.printed = false;
    }

    public int getId() { return id; }
    public int getOwnerId() { return ownerId; }
    public int getPages() { return pages; }
    public int getCopies() { return copies; }
    public int getPriority() { return priority; }
    public boolean isPrinted() { return printed; }

    public void markPrinted() { this.printed = true; }

    public abstract String getDetails();

    /** Общая часть сведений: id, владелец, потребность, статус. */
    protected String baseDetails(String type) {
        return type + "{id=" + id + ", owner=" + ownerId
                + ", pages=" + pages + ", copies=" + copies + ", priority=" + priority
                + ", sheets=" + getRequiredSheets()
                + ", status=" + (printed ? "напечатано" : "ожидает") + "}";
    }
}
