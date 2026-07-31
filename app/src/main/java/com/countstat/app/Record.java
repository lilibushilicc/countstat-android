package com.countstat.app;

import java.util.ArrayList;
import java.util.List;

/**
 * 计件记录数据模型。
 * 每条记录对应一天，包含若干台机器的产量与单价。
 */
public class Record {

    public String date = "";
    public long updatedAt = 0;
    public final List<Machine> machines = new ArrayList<>();

    /** 单台机器：名称、数量、单价。 */
    public static class Machine {
        /** 未配置单价时的默认值。 */
        public static final double DEFAULT_PRICE = 0.35;

        public String name = "";
        public int quantity = 0;
        public double unitPrice = DEFAULT_PRICE;

        public int total() {
            return quantity;
        }

        public double income() {
            return quantity * unitPrice;
        }
    }

    public Machine findMachine(String name) {
        for (Machine m : machines) {
            if (m.name.equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    /** 所有机器产量之和。 */
    public int total() {
        int sum = 0;
        for (Machine m : machines) sum += m.quantity;
        return sum;
    }

    /** 所有机器收入之和。 */
    public double income() {
        double sum = 0;
        for (Machine m : machines) sum += m.income();
        return sum;
    }

    /** 加权平均单价；无产量时返回 0。 */
    public double avgPrice() {
        int qty = total();
        if (qty <= 0) return 0;
        return income() / qty;
    }
}
