package edu.course.shop.service;

import edu.course.shop.exception.StorageException;
import edu.course.shop.exception.ValidationException;
import edu.course.shop.model.User;
import edu.course.shop.persistence.AdminRepository;
import edu.course.shop.persistence.DatabaseManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Admin-only, read-only store reporting. Inventory edits remain in ProductService. */
public final class AdminService {
    private final DatabaseManager database;
    private final AdminRepository reports;

    public AdminService(DatabaseManager database, AdminRepository reports) {
        this.database = database;
        this.reports = reports;
    }

    private void requireAdmin(User actor) {
        if (actor == null || !actor.canManageProducts()) throw new ValidationException("Administrator access is required.");
    }

    public AdminRepository.Summary summary(User actor) {
        requireAdmin(actor);
        try (Connection c = database.open()) { return reports.summary(c); }
        catch (SQLException e) { throw new StorageException(e); }
    }

    public List<AdminRepository.LowStockRow> lowStock(User actor) {
        requireAdmin(actor);
        try (Connection c = database.open()) { return reports.lowStock(c); }
        catch (SQLException e) { throw new StorageException(e); }
    }

    public List<AdminRepository.OrderRow> orders(User actor) {
        requireAdmin(actor);
        try (Connection c = database.open()) { return reports.orders(c); }
        catch (SQLException e) { throw new StorageException(e); }
    }

    public List<AdminRepository.CustomerRow> customers(User actor) {
        requireAdmin(actor);
        try (Connection c = database.open()) { return reports.customers(c); }
        catch (SQLException e) { throw new StorageException(e); }
    }
}
