package logic;

import models.PrintJob;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PrintQueue<T extends PrintJob> implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int MAX_PAPER = 1_000_000;

    private final List<T> jobs = new ArrayList<>();
    private int paper;

    public PrintQueue(int paper) {
        if (paper < 0 || paper > MAX_PAPER)
            throw new IllegalArgumentException("paper: 0..1000000");
        this.paper = paper;
    }

    public boolean add(T job) {
        if (job == null || job.isPrinted()) return false;
        boolean duplicate = jobs.stream().anyMatch(j -> j.getId() == job.getId());
        if (duplicate) return false;
        jobs.add(job);
        return true;
    }

    public int getPaper() {
        return paper;
    }

    public boolean restock(int amount) {
        if (amount <= 0) return false;
        if ((long) paper + amount > MAX_PAPER) return false;
        paper += amount;
        return true;
    }

    public Stream<T> getAnalyticsStream() {
        return new ArrayList<>(jobs).stream();
    }

    public List<T> process(Predicate<T> condition, Comparator<T> order, Consumer<T> action) {
        Objects.requireNonNull(condition);
        Objects.requireNonNull(order);
        Objects.requireNonNull(action);

        List<T> pending = jobs.stream()
                .filter(j -> !j.isPrinted())
                .sorted(order)
                .collect(Collectors.toList());

        List<T> done = new ArrayList<>();
        for (T job : pending) {
            int need = job.getRequiredSheets();
            if (need <= paper && condition.test(job)) {
                paper -= need;
                action.accept(job);
                done.add(job);
            }
        }
        return done;
    }
}
