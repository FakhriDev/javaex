package models;

public class DoubleSidedJob extends PrintJob {
    private static final long serialVersionUID = 1L;

    public DoubleSidedJob(int id, int ownerId, int pages, int copies, int priority) {
        super(id, ownerId, pages, copies, priority);
    }

    /** На каждый экземпляр — половина pages с округлением вверх; экземпляр начинается с нового листа. */
    @Override
    public int getRequiredSheets() {
        return (getPages() + 1) / 2 * getCopies();
    }

    @Override
    public String getDetails() {
        return baseDetails("DoubleSidedJob");
    }
}
