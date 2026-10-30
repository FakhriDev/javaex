package main;

import logic.PrintQueue;
import models.DoubleSidedJob;
import models.PrintJob;
import models.SingleSidedJob;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Main {
    private static final String STATE_FILE = "print_state.dat";
    private static final int INITIAL_PAPER = 14;
    private static final int RESTOCK_SHEETS = 5;   // строка В2: добавить 5 листов

    /** Сохраняемое состояние программы. */
    static final class AppState implements Serializable {
        private static final long serialVersionUID = 1L;
        PrintQueue<PrintJob> queue;   // null, пока этап 1 не выполнен
        int paper = INITIAL_PAPER;
        int n;
        int stageA;
        int stageB;                   // 0 — ещё не назначен
        int stageV;                   // 0 — ещё не назначен
        int lastDone;                 // 0..3
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        AppState state = loadOrCreate(sc);
        if (state == null) {
            return;
        }

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt(sc, "Ваш выбор: ");
            switch (choice) {
                case 1:
                    stage1(state);
                    break;
                case 2:
                    stage2(state);
                    break;
                case 3:
                    stage3(state);
                    break;
                case 4:
                    showQueue(state);
                    break;
                case 5:
                    if (save(state)) {
                        System.out.println("Состояние сохранено в " + STATE_FILE + ". Выход.");
                        running = false;
                    } else {
                        System.out.println("Возврат в меню с текущими данными.");
                    }
                    break;
                default:
                    System.out.println("Нет такого пункта меню (1..5).");
            }
        }
    }

    // ---------------------------------------------------------------- меню

    private static void printMenu() {
        System.out.println();
        System.out.println("=== Очередь печати ===");
        System.out.println("1. Выполнить задание 1 и вывести назначенный вариант задания 2");
        System.out.println("2. Выполнить задание 2 и вывести назначенный вариант задания 3");
        System.out.println("3. Выполнить задание 3 и вывести отчёт");
        System.out.println("4. Показать очередь, статусы и остаток бумаги");
        System.out.println("5. Выход из программы");
    }

    // ---------------------------------------------------------------- этап 1 (А1)

    private static List<PrintJob> createAllJobs() {
        List<PrintJob> all = new ArrayList<>();
        all.add(new SingleSidedJob(101, 11, 5, 1, 5));
        all.add(new SingleSidedJob(103, 12, 4, 2, 5));
        all.add(new SingleSidedJob(105, 13, 3, 2, 2));
        all.add(new SingleSidedJob(108, 14, 6, 1, 3));
        all.add(new SingleSidedJob(110, 15, 8, 1, 2));
        all.add(new DoubleSidedJob(102, 11, 5, 2, 4));
        all.add(new DoubleSidedJob(104, 12, 7, 1, 3));
        all.add(new DoubleSidedJob(106, 13, 9, 1, 4));
        all.add(new DoubleSidedJob(107, 14, 2, 3, 1));
        all.add(new DoubleSidedJob(109, 12, 1, 2, 4));
        return all;
    }

    private static void stage1(AppState s) {
        if (s.lastDone >= 1) {
            System.out.println("Этап 1 уже выполнен.");
            return;
        }
        // А1. Приоритетные короткие задания: priority >= 3 и потребность <= 8
        Predicate<PrintJob> condition =
                j -> j.getPriority() >= 3 && j.getRequiredSheets() <= 8;

        List<PrintJob> selected = createAllJobs().stream()
                .filter(condition)
                .sorted(Comparator.comparingInt(PrintJob::getId))
                .collect(Collectors.toList());

        PrintQueue<PrintJob> queue = new PrintQueue<>(INITIAL_PAPER);
        selected.forEach(queue::add);

        List<Integer> ids = selected.stream().map(PrintJob::getId).collect(Collectors.toList());
        int sheets = selected.stream().mapToInt(PrintJob::getRequiredSheets).sum();

        System.out.println("Добавлены задания (ID по возрастанию): " + ids);
        System.out.println("Сумма потребностей: " + sheets);

        int b;
        try {
            b = StageAccess.second(s.n, ids, sheets);
        } catch (IllegalStateException | IllegalArgumentException e) {
            System.out.println("Служебный код отклонил результат: " + e.getMessage());
            System.out.println("Проверьте решение этапа 1.");
            return;
        }
        s.queue = queue;
        s.paper = queue.getPaper();
        s.stageB = b;
        s.lastDone = 1;
        System.out.println("Назначенный вариант задания 2: Б" + b);
    }

    // ---------------------------------------------------------------- этап 2 (Б6)

    /** Б6: сначала владельцы с меньшим числом напечатанных на начало вызова; затем priority↓, id↑. */
    private static List<PrintJob> runRuleB6(PrintQueue<PrintJob> queue) {
        Map<Integer, Long> printedBefore = queue.getAnalyticsStream()
                .filter(PrintJob::isPrinted)
                .collect(Collectors.groupingBy(PrintJob::getOwnerId, Collectors.counting()));

        Comparator<PrintJob> order = Comparator
                .<PrintJob>comparingLong(j -> printedBefore.getOrDefault(j.getOwnerId(), 0L))
                .thenComparing(Comparator.comparingInt(PrintJob::getPriority).reversed())
                .thenComparingInt(PrintJob::getId);

        return queue.process(j -> true, order, PrintJob::markPrinted);
    }

    private static void stage2(AppState s) {
        if (s.lastDone >= 2) {
            System.out.println("Этап 2 уже выполнен.");
            return;
        }
        if (s.lastDone < 1) {
            System.out.println("Сначала выполните этап 1.");
            return;
        }
        PrintQueue<PrintJob> work;
        try {
            work = deepCopy(s.queue);   // чтобы отклонённый результат не менял данные
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Внутренняя ошибка копирования очереди: " + e.getMessage());
            return;
        }
        List<PrintJob> done = runRuleB6(work);
        List<Integer> ids = done.stream().map(PrintJob::getId).collect(Collectors.toList());
        int paper = work.getPaper();

        System.out.println("Выполнены (по порядку): " + ids);
        System.out.println("Остаток бумаги: " + paper);

        int v;
        try {
            v = StageAccess.third(s.n, ids, paper);
        } catch (IllegalStateException | IllegalArgumentException e) {
            System.out.println("Служебный код отклонил результат: " + e.getMessage());
            System.out.println("Проверьте решение этапа 2. Данные не изменены.");
            return;
        }
        s.queue = work;
        s.paper = paper;
        s.stageV = v;
        s.lastDone = 2;
        System.out.println("Назначенный вариант задания 3: В" + v);
    }

    // ---------------------------------------------------------------- этап 3 (В2)

    private static void stage3(AppState s) {
        if (s.lastDone >= 3) {
            System.out.println("Этап 3 уже выполнен.");
            return;
        }
        if (s.lastDone < 2) {
            System.out.println("Сначала выполните этап " + (s.lastDone + 1) + ".");
            return;
        }
        if (!s.queue.restock(RESTOCK_SHEETS)) {
            System.out.println("Не удалось пополнить бумагу. Данные не изменены.");
            return;
        }
        List<PrintJob> done = runRuleB6(s.queue);
        s.paper = s.queue.getPaper();
        s.lastDone = 3;

        System.out.println("Добавлено листов: " + RESTOCK_SHEETS);
        System.out.println("Новые выполненные (по порядку): "
                + done.stream().map(PrintJob::getId).collect(Collectors.toList()));
        System.out.println("Остаток бумаги: " + s.paper);

        // В2: ID владельцев (по возрастанию), у которых одновременно есть
        // напечатанные и ожидающие задания.
        List<Integer> report = s.queue.getAnalyticsStream()
                .collect(Collectors.groupingBy(PrintJob::getOwnerId,
                        Collectors.mapping(PrintJob::isPrinted, Collectors.toSet())))
                .entrySet().stream()
                .filter(e -> e.getValue().size() == 2)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
        System.out.println("Отчёт В2 (владельцы с напечатанными и ожидающими заданиями): " + report);
    }

    // ---------------------------------------------------------------- просмотр

    private static void showQueue(AppState s) {
        if (s.queue == null) {
            System.out.println("Очередь пуста (этап 1 не выполнен).");
            System.out.println("Остаток бумаги: " + s.paper);
            return;
        }
        System.out.println("Очередь:");
        s.queue.getAnalyticsStream().forEach(j -> System.out.println("  " + j.getDetails()));
        System.out.println("Остаток бумаги: " + s.queue.getPaper());
        System.out.println("N = " + s.n + ", А" + s.stageA
                + (s.stageB > 0 ? ", Б" + s.stageB : "")
                + (s.stageV > 0 ? ", В" + s.stageV : "")
                + "; последний завершённый этап: " + s.lastDone);
    }

    // ---------------------------------------------------------------- ввод

    private static int readInt(Scanner sc, String prompt) {
        while (true) {
            System.out.print(prompt);
            if (!sc.hasNextLine()) {          // конец ввода — как выход
                return 5;
            }
            String line = sc.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка ввода: нужно целое число.");
            }
        }
    }

    private static int readN(Scanner sc) {
        while (true) {
            int n = readInt(sc, "Введите начальный номер N (1..36): ");
            if (n >= 1 && n <= 36) {
                return n;
            }
            System.out.println("N должно быть от 1 до 36.");
        }
    }

    // ---------------------------------------------------------------- сохранение / загрузка

    private static AppState newChain(Scanner sc) {
        AppState s = new AppState();
        s.n = readN(sc);
        s.stageA = StageAccess.initial(s.n);
        System.out.println("Назначенный вариант задания 1: А" + s.stageA);
        return s;
    }

    private static AppState loadOrCreate(Scanner sc) {
        File file = new File(STATE_FILE);
        if (!file.exists()) {
            return newChain(sc);
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            AppState s = (AppState) in.readObject();
            if (s.queue != null) {
                s.paper = s.queue.getPaper();
            }
            System.out.println("Состояние загружено из " + STATE_FILE
                    + " (N = " + s.n + ", последний завершённый этап: " + s.lastDone + ").");
            return s;
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            System.out.println("Ошибка чтения " + STATE_FILE + ": " + e);
            System.out.println("Запустите программу заново: удалите повреждённый файл "
                    + "или начните новую цепочку (при выходе файл будет перезаписан).");
            System.out.print("Начать новую цепочку сейчас? (y/n): ");
            if (sc.hasNextLine() && sc.nextLine().trim().equalsIgnoreCase("y")) {
                return newChain(sc);
            }
            return null;
        }
    }

    private static boolean save(AppState s) {
        if (s.queue != null) {
            s.paper = s.queue.getPaper();
        }
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(STATE_FILE))) {
            out.writeObject(s);
            return true;
        } catch (IOException e) {
            System.out.println("Ошибка записи " + STATE_FILE + ": " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static PrintQueue<PrintJob> deepCopy(PrintQueue<PrintJob> q)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(q);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (PrintQueue<PrintJob>) in.readObject();
        }
    }
}

final class StageAccess {
    private static final int[] S = {5, 3, 2, 4, 2, 1};
    private static final int[] A = {
        0xcb89387d, 0x4626b7fe, 0xacac2431, 0x4997263f, 0x6b20cc2b,
        0x209a21ae
    };
    private static final int[] B = {
        0x6128024d, 0x38a00d09, 0x38a2c749, 0x2ad3a538, 0x38a00d09,
        0x38a00d09, 0x612876ab, 0x612ba548, 0xe7a1669a, 0x612876ab,
        0xc4f9d0bb, 0x612876ab, 0xf3601d34, 0x013dc91f, 0xf3601d34,
        0x4471d939, 0x38a2c749, 0x2ad3a538, 0x6128024d, 0x38a00d09,
        0x38a16a29, 0x04b3adf2, 0x38a00d09, 0x38a00d09, 0x38a00d09,
        0x38a00d09, 0x6128024d, 0x38a00d09, 0x38a16a29, 0x04b3adf2,
        0x013dc91f, 0x4471d939, 0x4471d939, 0x013e3d80, 0x0d1d0f0f,
        0x4471d939
    };
    static int initial(int n) {
        if (n < 1 || n > 36)
            throw new IllegalArgumentException("N: 1..36");
        return (n - 1) / 6 + 1;
    }
    static int second(int n, java.util.List<Integer> ids, int sheets) {
        int a = initial(n);
        return check(n, ids, sheets, A[a - 1], 0);
    }
    static int third(int n, java.util.List<Integer> ids, int paper) {
        int a = initial(n);
        int b = 1 + Math.floorMod((long) A[a - 1] + (n - 1) % 6, 6);
        return check(n, ids, paper, B[n - 1], b);
    }
    private static int check(int n, java.util.List<Integer> ids,
                             int value, int expected, int previous) {
        if (ids == null) throw new IllegalArgumentException("ids: null");
        int actual = (ids + "|" + value).hashCode();
        if (actual != expected)
            throw new IllegalStateException("Check stage result");
        long key = previous == 0 ? actual
            : (actual >>> 6) + 0L * previous
            + (1 - 1L) * ((n - 1) % 6) + S[initial(n) - 1];
        return 1 + Math.floorMod(key + (n - 1) % 6, 6);
    }
}
