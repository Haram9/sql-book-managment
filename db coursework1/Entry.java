

import java.sql.*;
import java.util.Scanner;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.Vector;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;

public class Entry {

    public static final String DB_URL = "jdbc:mysql://localhost:3306/BeaconBooksDB?useSSL=false&allowPublicKeyRetrieval=true";
    public static final String USER = "root";
    public static final String PASS = "972003";

    private static final Scanner S = new Scanner(System.in);
    private static Connection c = null;

    public static void main(String[] args) {
        try {
            System.out.println("--- SYSTEM STARTUP ---");
            System.out.print("Connecting to database... ");

            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                System.out.println("FAILED.");
                System.err.println("CRITICAL: MySQL Driver JAR missing from Libraries.");
                return;
            }

            c = DriverManager.getConnection(DB_URL, USER, PASS);
            System.out.println("SUCCESS.");

            // Connection Test (for consistency in console and GUI)
            System.out.println("Testing connection...");
            try (Statement stmt = c.createStatement()) {
                 ResultSet rs = stmt.executeQuery("SELECT DATABASE()");
                 if (rs.next()) {
                      System.out.println("Connected to database: " + rs.getString(1));
                 }
            } catch (SQLException e) {
                 System.out.println("Error: " + e.getMessage());
            }

            try {
                 UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception ignored) {}


            boolean running = true;

            while (running) {
                System.out.println("\n=================================");
                System.out.println("   BEACON BOOKS MANAGER");
                System.out.println("=================================");
                System.out.println("1 - Browse Database (View Data)");
                System.out.println("2 - Schedule Delivery (Execute Procedure)");
                System.out.println("G - Launch GUI Application (CRUD)"); 
                System.out.println("Q - Quit");
                
                String choice = readInput("Select Option: ").toUpperCase();

                switch (choice) {
                    case "1": 
                        browseResultSet(); 
                        break;
                    case "2": 
                        invokeProcedure(); 
                        break;
                    case "G":
                        System.out.println(">>> Launching Graphical Interface...");
                        SwingUtilities.invokeLater(() -> {
                            new BeaconGUI().setVisible(true);
                        });
                        break;
                    case "Q": 
                        running = false; 
                        System.out.println("Closing connection..."); 
                        break;
                    default: 
                        System.out.println(">>> Invalid option.");
                }
            }

        } catch (SQLException e) {
            System.err.println("\n*** DATABASE ERROR ***");
            System.err.println("Message: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { if (c != null && !c.isClosed()) c.close(); } catch (SQLException e) {}
            S.close();
            System.out.println("System Halted.");
        }
    }

    private static void browseResultSet() {
        System.out.println("\n--- BROWSE MENU ---");
        System.out.println("1 - Upcoming Orders (Coursework Req)");
        System.out.println("2 - Table: Book");
        System.out.println("3 - Table: Customer");
        System.out.println("4 - Table: Orders");
        System.out.println("5 - Table: Purchase");
        System.out.println("6 - Table: Category");
        System.out.println("7 - Table: Review Audit (Check Triggers)");
        System.out.println("0 - Go Back");
        
        String choice = readInput("View Table: ");
        String sql = "";

        switch (choice) {
            case "1":
                // Refined query to show upcoming orders, joining to get the title
                sql = "SELECT o.order_id, b.code, b.title, o.order_date, o.delivery_address " +
                      "FROM `orders` o " +
                      "JOIN book b ON o.book_code = b.code " + 
                      "WHERE o.order_date > CURRENT_DATE " +
                      "ORDER BY o.order_date ASC";
                break;
            case "2": sql = "SELECT * FROM book"; break;
            case "3": sql = "SELECT * FROM customer"; break;
            case "4": sql = "SELECT * FROM `orders`"; break; 
            case "5": sql = "SELECT * FROM purchase"; break;
            case "6": sql = "SELECT * FROM category"; break;
            case "7": sql = "SELECT * FROM review_audit"; break;
            case "0": return; 
            default: 
                System.out.println(">>> Invalid selection."); 
                return;
        }

        printTable(sql);
    }

    private static void invokeProcedure() {
        System.out.println("\n--- SCHEDULE DELIVERY ---");
        
        String catCode;
        while (true) {
            catCode = readInput("Enter Category Code (e.g. WSD): ");
            if (!catCode.isEmpty()) break;
            System.out.println(">>> Code cannot be empty.");
        }

        String dateStr;
        while (true) {
            dateStr = readInput("Enter Start Date (YYYY-MM-DD): ");
            if (Pattern.matches("^\\d{4}-\\d{2}-\\d{2}$", dateStr)) break;
            System.out.println(">>> Invalid Format! Use YYYY-MM-DD (e.g., 2026-05-01).");
        }

        String query = "{CALL schedule_book_deliveries(?, ?)}";
        
        try (CallableStatement cs = c.prepareCall(query)) {
            
            cs.setString(1, catCode.toUpperCase());
            cs.setDate(2, Date.valueOf(dateStr));

            System.out.print("Executing Procedure... ");
            cs.execute();
            System.out.println("SUCCESS! Deliveries updated.");

        } catch (SQLException e) {
            System.out.println("\n>>> PROCEDURE FAILED");
            System.out.println("Reason: " + e.getMessage());
            // This hint assists the user based on the expected SQL validation error
            if (e.getMessage().contains("Start date")) { 
                System.out.println("Hint: Date must be at least 1 month in the future.");
            }
        }
    }

    private static String readInput(String prompt) {
        System.out.print(prompt);
        return S.nextLine().trim();
    }

    private static void printTable(String sql) {
        try (Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            ResultSetMetaData metaData = rs.getMetaData();
            int colCount = metaData.getColumnCount();

            System.out.println("\n--- RESULTS ---");
            
            for (int i = 1; i <= colCount; i++) {
                System.out.printf("%-20s", metaData.getColumnLabel(i));
            }
            System.out.println();
            for (int i = 1; i <= colCount; i++) System.out.print("--------------------");
            System.out.println();

            boolean hasRows = false;
            while (rs.next()) {
                hasRows = true;
                for (int i = 1; i <= colCount; i++) {
                    String val = rs.getString(i);
                    System.out.printf("%-20s", (val == null ? "NULL" : val));
                }
                System.out.println();
            }

            if (!hasRows) System.out.println("(No records found)");
            System.out.println("----------------------------------------");

        } catch (SQLException e) {
            System.out.println(">>> Error reading data: " + e.getMessage());
        }
    }
}

class BeaconGUI extends JFrame {

    private static final Map<String, LinkedHashMap<String, String>> TABLE_CONFIG = new LinkedHashMap<String, LinkedHashMap<String, String>>();
    static {
        TABLE_CONFIG.put("orders", new LinkedHashMap<String, String>() {{
            put("Order ID (PK)", "order_id");
            put("Order Date", "order_date");
            put("Address", "delivery_address");
            put("Customer ID (FK)", "customer_id");
            put("Book Code (FK)", "book_code");
        }});
        TABLE_CONFIG.put("book", new LinkedHashMap<String, String>() {{
            put("Book Code (PK)", "code");
            put("Title", "title");
            put("Price", "price");
            put("Category Code (FK)", "category_code");
            put("Delivery Date", "delivery_date");
        }});
        TABLE_CONFIG.put("customer", new LinkedHashMap<String, String>() {{
            put("Customer No (PK)", "no");
            put("Name", "name");
            put("Email", "email");
            put("Phone", "phone_number");
        }});
        TABLE_CONFIG.put("category", new LinkedHashMap<String, String>() {{
            put("Category Code (PK)", "code");
            put("Category Name", "name");
        }});
        // Tables with Composite Keys or Audit
        TABLE_CONFIG.put("purchase", new LinkedHashMap<String, String>() {{
            put("Customer ID (Key 1)", "customer_id"); 
            put("Book Code (Key 2)", "book_code");     
            put("Review Score", "review_score");
        }});
        TABLE_CONFIG.put("review_audit", new LinkedHashMap<String, String>() {{
            put("Audit ID (PK)", "audit_id"); 
            put("Customer ID", "customer_id");
            put("Book Code", "book_code");
            put("Old Score", "old_score");
            put("New Score", "new_score");
            put("Changed By", "changed_by");
            put("Change Date", "change_date");
        }});
    }
    
    private JComboBox<String> cmbTableSelector;
    private JTable table;
    private DefaultTableModel model;
    private JPanel formPanel;
    private JTextField txtPKField;
    private java.util.List<JTextField> txtFields;
    private JLabel lblStatus;

    private String currentTableName = "orders";
    private String currentPKColumn = "order_id";
    private java.util.List<String> currentNonPKColumns;

    public BeaconGUI() {
        setTitle("Beacon Books - Dynamic CRUD Manager");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); 
        setLayout(new BorderLayout(15, 15));
        
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 10));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

        controlPanel.add(new JLabel("Select Table to Edit:", SwingConstants.RIGHT));
        cmbTableSelector = new JComboBox<>(TABLE_CONFIG.keySet().toArray(new String[0]));
        cmbTableSelector.addActionListener(e -> switchTable());
        controlPanel.add(cmbTableSelector);
        
        lblStatus = new JLabel("Status: Initializing...", SwingConstants.CENTER);
        lblStatus.setForeground(Color.BLUE);
        controlPanel.add(lblStatus);

        add(controlPanel, BorderLayout.NORTH);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.2);
        
        formPanel = new JPanel(new BorderLayout(10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        splitPane.setTopComponent(formPanel);

        model = new DefaultTableModel();
        table = new JTable(model);
        table.setRowHeight(25);
        table.getTableHeader().setReorderingAllowed(false);

        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                fillFormFromTable();
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        splitPane.setBottomComponent(scrollPane);

        add(splitPane, BorderLayout.CENTER);


        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 15));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        
        JButton btnAdd = new JButton("➕ ADD Record");
        JButton btnUpdate = new JButton("✍️ UPDATE Record");
        JButton btnDelete = new JButton("❌ DELETE Record");
        JButton btnClear = new JButton("🔄 CLEAR Form");

        styleButton(btnAdd, new Color(50, 205, 50)); 
        styleButton(btnUpdate, new Color(30, 144, 255)); 
        styleButton(btnDelete, new Color(220, 20, 60)); 
        styleButton(btnClear, Color.GRAY);

        buttonPanel.add(btnAdd);
        buttonPanel.add(btnUpdate);
        buttonPanel.add(btnDelete);
        buttonPanel.add(btnClear);

        add(buttonPanel, BorderLayout.SOUTH);

        btnAdd.addActionListener(e -> executeCRUD("INSERT"));
        btnUpdate.addActionListener(e -> executeCRUD("UPDATE"));
        btnDelete.addActionListener(e -> executeCRUD("DELETE"));
        btnClear.addActionListener(e -> clearFields());

        checkConnectionAndInitStatus();
        switchTable();
    }
    
    private void checkConnectionAndInitStatus() {
        try (Connection conn = DriverManager.getConnection(Entry.DB_URL, Entry.USER, Entry.PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DATABASE()")) {

            if (rs.next()) {
                lblStatus.setText("Status: Connected to DB: " + rs.getString(1));
                lblStatus.setForeground(new Color(0, 150, 0));
            } else {
                lblStatus.setText("Status: Connection Check Failed.");
                lblStatus.setForeground(Color.RED);
            }
        } catch (SQLException e) {
            lblStatus.setText("Status: Connection Error! " + e.getMessage());
            lblStatus.setForeground(Color.RED);
            JOptionPane.showMessageDialog(this, "CRITICAL DATABASE ERROR: Could not connect to database. Check server and credentials.\nDetails: " + e.getMessage(), "Connection Failed", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void switchTable() {
        currentTableName = (String) cmbTableSelector.getSelectedItem();
        LinkedHashMap<String, String> config = TABLE_CONFIG.get(currentTableName);
        
        currentNonPKColumns = new Vector<String>();
        
        boolean first = true;
        
        for (Map.Entry<String, String> entry : config.entrySet()) {
            if (first) {
                currentPKColumn = entry.getValue();
                first = false;
            } else {
                currentNonPKColumns.add(entry.getValue());
            }
        }
        
        rebuildForm(config);
        
        loadData();
        
        if (lblStatus.getForeground().equals(new Color(0, 150, 0)) || lblStatus.getForeground().equals(Color.BLUE)) {
            lblStatus.setText("Status: Editing table " + currentTableName.toUpperCase());
        }
    }
    
    private void rebuildForm(LinkedHashMap<String, String> config) {
        formPanel.removeAll();
        formPanel.setLayout(new BorderLayout(10, 10));
        
        JPanel fieldsPanel = new JPanel(new GridLayout(config.size(), 2, 10, 10));
        fieldsPanel.setBorder(BorderFactory.createTitledBorder("Input Form: " + currentTableName.toUpperCase()));
        
        txtFields = new Vector<JTextField>();
        
        boolean isComposite = currentTableName.equals("purchase");
        int keyIndex = 0;
        
        for (Map.Entry<String, String> entry : config.entrySet()) {
            String label = entry.getKey();
            
            JTextField field = new JTextField();
            fieldsPanel.add(new JLabel("  " + label + ":", SwingConstants.RIGHT));
            fieldsPanel.add(field);
            
            if (keyIndex == 0) {
                txtPKField = field;
                if (!isComposite) { 
                    txtPKField.setEditable(false);
                    txtPKField.setBackground(new Color(240, 240, 240));
                }
            }
            
            if (isComposite && keyIndex < 2) {
                // Key components (for composite key) are disabled after initial selection
                field.setEditable(false);
                field.setBackground(new Color(240, 240, 240));
                if(keyIndex == 1) txtFields.add(field); // Key 2 is the first element in txtFields
            } else if (!isComposite && keyIndex > 0) {
                txtFields.add(field); // Add non-PK fields
            }
            
            if (isComposite && keyIndex >= 2) { // Add non-key fields for purchase (only review_score)
                 txtFields.add(field);
            }
            
            keyIndex++;
        }
        
        formPanel.add(fieldsPanel, BorderLayout.CENTER);
        formPanel.revalidate();
        formPanel.repaint();
    }
    
    private void loadData() {
        model.setRowCount(0);
        model.setColumnCount(0);

        for (String label : TABLE_CONFIG.get(currentTableName).keySet()) {
            model.addColumn(label);
        }

        try (Connection conn = DriverManager.getConnection(Entry.DB_URL, Entry.USER, Entry.PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM `" + currentTableName + "`")) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            while (rs.next()) {
                Vector<String> row = new Vector<String>();
                for (int i = 1; i <= colCount; i++) {
                    row.add(rs.getString(i));
                }
                model.addRow(row);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database Error loading " + currentTableName + ": " + ex.getMessage());
        }
    }
    
    private void fillFormFromTable() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        
        boolean isComposite = currentTableName.equals("purchase");
        int offset = isComposite ? 2 : 1; 
        
        txtPKField.setText(model.getValueAt(row, 0).toString());
        
        for (int i = 0; i < txtFields.size(); i++) {
            Object value = model.getValueAt(row, i + offset);
            txtFields.get(i).setText(value != null ? value.toString() : "");
        }
    }

    private void executeCRUD(String operation) {
        String sql;
        java.util.List<String> args = new Vector<String>();
        
        String pkValue = txtPKField.getText().trim();
        boolean isComposite = currentTableName.equals("purchase");

        switch (operation) {
            case "INSERT":
                args.add(pkValue); // Key 1
                for (JTextField field : txtFields) {
                    args.add(field.getText().trim());
                }
                
                java.util.List<String> allCols = new ArrayList<>(TABLE_CONFIG.get(currentTableName).values());
                String cols = String.join(", ", allCols);
                String placeholders = String.join(", ", java.util.Collections.nCopies(allCols.size(), "?"));
                
                sql = String.format("INSERT INTO `%s` (%s) VALUES (%s)", currentTableName, cols, placeholders);
                break;
                
            case "UPDATE":
                if (pkValue.isEmpty()) { JOptionPane.showMessageDialog(this, "Select a record to update first!"); return; }
                
                java.util.List<String> setClauses = new Vector<String>();
                
                // Fields to UPDATE (only non-key, updatable fields)
                for (int i = isComposite ? 2 : 1; i < TABLE_CONFIG.get(currentTableName).size(); i++) {
                    String colName = new ArrayList<>(TABLE_CONFIG.get(currentTableName).values()).get(i);
                    setClauses.add(colName + "=?");
                }
                
                // Arguments for SET clause
                for (int i = isComposite ? 1 : 0; i < txtFields.size(); i++) {
                    args.add(txtFields.get(i).getText().trim());
                }
                
                // Add WHERE clause keys to the end of args list
                if (isComposite) {
                    // Purchase: WHERE Key1=? AND Key2=?
                    sql = String.format("UPDATE `%s` SET %s WHERE %s=? AND %s=?", 
                                         currentTableName, 
                                         String.join(", ", setClauses), 
                                         currentPKColumn, 
                                         currentNonPKColumns.get(0));
                    args.add(pkValue); // Key 1: customer_id
                    args.add(txtFields.get(0).getText().trim()); // Key 2: book_code
                } else {
                    // Standard Single PK: WHERE Key=?
                    sql = String.format("UPDATE `%s` SET %s WHERE %s=?", 
                                         currentTableName, 
                                         String.join(", ", setClauses), 
                                         currentPKColumn);
                    args.add(pkValue);
                }
                break;
                
            case "DELETE":
                if (pkValue.isEmpty()) { JOptionPane.showMessageDialog(this, "Select a record to delete first!"); return; }
                
                int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete PK " + pkValue + " from " + currentTableName + "?");
                if (confirm != JOptionPane.YES_OPTION) return;
                
                if (isComposite) {
                    // Purchase: WHERE Key1=? AND Key2=?
                    sql = String.format("DELETE FROM `%s` WHERE %s=? AND %s=?", 
                                         currentTableName, 
                                         currentPKColumn, 
                                         currentNonPKColumns.get(0));
                    args.add(pkValue); // Key 1: customer_id
                    args.add(txtFields.get(0).getText().trim()); // Key 2: book_code
                } else {
                    // Standard Single PK: WHERE Key=?
                    sql = String.format("DELETE FROM `%s` WHERE %s=?", currentTableName, currentPKColumn);
                    args.add(pkValue);
                }
                break;
                
            default: return;
        }

        try (Connection conn = DriverManager.getConnection(Entry.DB_URL, Entry.USER, Entry.PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.isEmpty()) {
                    pstmt.setNull(i + 1, Types.VARCHAR);
                } else {
                     pstmt.setString(i + 1, arg);
                }
            }
            
            int rowsAffected = pstmt.executeUpdate();
            
            loadData();
            clearFields();
            lblStatus.setText("Status: " + operation + " Successful! (" + rowsAffected + " rows)");

        } catch (SQLException ex) {
            lblStatus.setText("Status: ERROR");
            JOptionPane.showMessageDialog(this, operation + " Error: " + ex.getMessage() + "\nSQL: " + sql + "\nArgs: " + args, "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearFields() {
        if (txtPKField != null) txtPKField.setText("");
        for (JTextField field : txtFields) {
            field.setText("");
        }
        lblStatus.setText("Status: Form cleared.");
    }
    
    private void styleButton(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Arial", Font.BOLD, 14));
        btn.setFocusPainted(false);
    }
}