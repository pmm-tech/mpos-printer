package id.modefashion.printer.backend;

import java.util.List;

import id.modefashion.printer.dto.ReceiptLineData;

public interface PrinterBackend {

  void print(List<ReceiptLineData> data, boolean formFeed);
}
