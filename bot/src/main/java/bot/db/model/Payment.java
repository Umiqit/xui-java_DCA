package bot.db.model;

import java.math.BigDecimal;

public class Payment {
    public long id;
    public long userId;
    public BigDecimal amount;
    public String currency;
    public String payload;
    public String status;
    public String createdAt;

    public Payment() {}
}
