package models;

public class SingleSidedJob extends PrintJob {
    private static final long serialVersionUID = 1L;

    public SingleSidedJob(int id, int ownerId, int pages, int copies, int priority) {
        super(id, ownerId, pages, copies, priority);
    }

    @Override
    public int getRequiredSheets() {
        return getPages() * getCopies();
    }

    @Override
    public String getDetails() {
        return baseDetails("SingleSidedJob");
    }
}
