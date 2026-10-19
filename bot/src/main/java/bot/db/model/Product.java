package bot.db.model;

import java.math.BigDecimal;

public class Product {
    public long id;
    public String name;
    public String description;
    public BigDecimal price;
    public int durationDays;
    public int trafficGb;
    public int inboundId;
    public int sortOrder;
    public boolean active;

    public Product() {}

    public Product(long id, String name, String description, BigDecimal price, int durationDays, int trafficGb, int inboundId, int sortOrder, boolean active) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.durationDays = durationDays;
        this.trafficGb = trafficGb;
        this.inboundId = inboundId;
        this.sortOrder = sortOrder;
        this.active = active;
    }
}
