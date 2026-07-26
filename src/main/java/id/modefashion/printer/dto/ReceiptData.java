package id.modefashion.printer.dto;

import java.util.List;
import lombok.Data;

@Data
public class ReceiptData {
  private int total;
  private List<ReceiptLineData> data;
  private String printer;
  private boolean formFeed;
}
