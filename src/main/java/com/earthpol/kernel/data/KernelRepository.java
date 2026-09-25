package com.earthpol.kernel.data;

import com.earthpol.kernel.config.KernelSettings;
import com.earthpol.kernel.data.model.BalanceEntry;
import com.earthpol.kernel.data.model.BalanceOperation;
import com.earthpol.kernel.data.model.BalanceTransfer;
import com.earthpol.kernel.data.model.HomeRecord;
import com.earthpol.kernel.data.model.MailRecord;
import com.earthpol.kernel.data.model.PlayerLookup;
import com.earthpol.kernel.data.model.StoredPlayer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class KernelRepository {

    private final KernelDatabase database;
    private final KernelSettings settings;

    public KernelRepository(KernelDatabase database, KernelSettings settings) {
        this.database = database;
        this.settings = settings;
    }

    public synchronized StoredPlayer ensurePlayer(Player player) {
        return ensurePlayer(player.getUniqueId(), player.getName(), extractIp(player));
    }

    public synchronized StoredPlayer ensurePlayer(UUID uuid, String name) {
        return ensurePlayer(uuid, name, null);
    }

    public synchronized StoredPlayer ensurePlayer(UUID uuid, String name, @Nullable String ipAddress) {
        long now = Instant.now().toEpochMilli();

        try (PreparedStatement statement = database.connection().prepareStatement("""
            INSERT INTO players (
                uuid,
                last_known_name,
                last_known_name_lower,
                last_known_ip,
                balance,
                last_seen_epoch_ms
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                last_known_name = excluded.last_known_name,
                last_known_name_lower = excluded.last_known_name_lower,
                last_known_ip = COALESCE(excluded.last_known_ip, players.last_known_ip),
                last_seen_epoch_ms = excluded.last_seen_epoch_ms
            """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setString(3, name.toLowerCase(Locale.ROOT));
            statement.setString(4, ipAddress);
            statement.setString(5, settings.startingBalance().toPlainString());
            statement.setLong(6, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to ensure a player row exists.", exception);
        }

        return findPlayer(uuid).orElseThrow();
    }

    public synchronized Optional<StoredPlayer> findPlayer(UUID uuid) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
            SELECT uuid, last_known_name, last_known_ip, balance, nickname, god_mode, fly_mode, teleport_blocked, last_seen_epoch_ms
            FROM players
            WHERE uuid = ?
            """)) {
            statement.setString(1, uuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(readPlayer(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load a player row.", exception);
        }
    }

    public synchronized Optional<PlayerLookup> findPlayerByName(String name) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
            SELECT uuid, last_known_name
            FROM players
            WHERE last_known_name_lower = ?
            ORDER BY last_seen_epoch_ms DESC
            LIMIT 1
            """)) {
            statement.setString(1, name.toLowerCase(Locale.ROOT));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(new PlayerLookup(
                    UUID.fromString(resultSet.getString("uuid")),
                    resultSet.getString("last_known_name")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to look up a player by name.", exception);
        }
    }

    public synchronized List<String> listHomes(UUID ownerUuid) {
        List<String> homes = new ArrayList<>();

        try (PreparedStatement statement = database.connection().prepareStatement("""
            SELECT home_name
            FROM homes
            WHERE owner_uuid = ?
            ORDER BY home_name ASC
            """)) {
            statement.setString(1, ownerUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    homes.add(resultSet.getString("home_name"));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load homes.", exception);
        }

        return homes;
    }

    public synchronized Optional<HomeRecord> findHome(UUID ownerUuid, String homeName) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
            SELECT home_name, world_name, x, y, z, yaw, pitch
            FROM homes
            WHERE owner_uuid = ? AND home_name = ?
            """)) {
            statement.setString(1, ownerUuid.toString());
            statement.setString(2, homeName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(new HomeRecord(
                    resultSet.getString("home_name"),
                    resultSet.getString("world_name"),
                    resultSet.getDouble("x"),
                    resultSet.getDouble("y"),
                    resultSet.getDouble("z"),
                    resultSet.getFloat("yaw"),
                    resultSet.getFloat("pitch")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load a home.", exception);
        }
    }

    public synchronized void upsertHome(UUID ownerUuid, String homeName, Location location) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
            INSERT INTO homes (owner_uuid, home_name, world_name, x, y, z, yaw, pitch)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(owner_uuid, home_name) DO UPDATE SET
                world_name = excluded.world_name,
                x = excluded.x,
                y = excluded.y,
                z = excluded.z,
                yaw = excluded.yaw,
                pitch = excluded.pitch
            """)) {
            statement.setString(1, ownerUuid.toString());
            statement.setString(2, homeName);
            statement.setString(3, location.getWorld().getName());
            statement.setDouble(4, location.getX());
            statement.setDouble(5, location.getY());
            statement.setDouble(6, location.getZ());
            statement.setFloat(7, location.getYaw());
            statement.setFloat(8, location.getPitch());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save a home.", exception);
        }
    }

    public synchronized boolean deleteHome(UUID ownerUuid, String homeName) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
            DELETE FROM homes
            WHERE owner_uuid = ? AND home_name = ?
            """)) {
            statement.setString(1, ownerUuid.toString());
            statement.setString(2, homeName);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete a home.", exception);
        }
    }

    public synchronized boolean isIgnoring(UUID ownerUuid, UUID targetUuid) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
            SELECT 1
            FROM ignores
            WHERE owner_uuid = ? AND target_uuid = ?
            """)) {
            statement.setString(1, ownerUuid.toString());
            statement.setString(2, targetUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to check ignore state.", exception);
        }
    }

    public synchronized boolean toggleIgnore(UUID ownerUuid, UUID targetUuid) {
        if (isIgnoring(ownerUuid, targetUuid)) {
            try (PreparedStatement statement = database.connection().prepareStatement("""
                DELETE FROM ignores
                WHERE owner_uuid = ? AND target_uuid = ?
                """)) {
                statement.setString(1, ownerUuid.toString());
                statement.setString(2, targetUuid.toString());
                statement.executeUpdate();
                return false;
            } catch (SQLException exception) {
                throw new IllegalStateException("Unable to remove ignore state.", exception);
            }
        }

        try (PreparedStatement statement = database.connection().prepareStatement("""
            INSERT INTO ignores (owner_uuid, target_uuid)
            VALUES (?, ?)
            """)) {
            statement.setString(1, ownerUuid.toString());
            statement.setString(2, targetUuid.toString());
            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save ignore state.", exception);
        }
    }

    public synchronized List<PlayerLookup> getIgnored(UUID ownerUuid) {
        List<PlayerLookup> ignored = new ArrayList<>();

        try (PreparedStatement statement = database.connection().prepareStatement("""
            SELECT players.uuid, players.last_known_name
            FROM ignores
            JOIN players ON players.uuid = ignores.target_uuid
            WHERE ignores.owner_uuid = ?
            ORDER BY players.last_known_name_lower ASC
            """)) {
            statement.setString(1, ownerUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ignored.add(new PlayerLookup(
                        UUID.fromString(resultSet.getString("uuid")),
                        resultSet.getString("last_known_name")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load ignored players.", exception);
        }

        return ignored;
    }

    public synchronized List<PlayerLookup> getIgnoredBy(UUID targetUuid) {
        List<PlayerLookup> ignoredBy = new ArrayList<>();

        try (PreparedStatement statement = database.connection().prepareStatement("""
            SELECT players.uuid, players.last_known_name
            FROM ignores
            JOIN players ON players.uuid = ignores.owner_uuid
            WHERE ignores.target_uuid = ?
            ORDER BY players.last_known_name_lower ASC
            """)) {
            statement.setString(1, targetUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ignoredBy.add(new PlayerLookup(
                        UUID.fromString(resultSet.getString("uuid")),
                        resultSet.getString("last_known_name")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load players who ignore a target.", exception);
        }

        return ignoredBy;
    }

    public synchronized long saveMail(UUID recipientUuid, UUID senderUuid, String senderName, String message) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
            INSERT INTO mail (recipient_uuid, sender_uuid, sender_name, message, sent_epoch_ms)
            VALUES (?, ?, ?, ?, ?)
            """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, recipientUuid.toString());
            statement.setString(2, senderUuid == null ? null : senderUuid.toString());
            statement.setString(3, senderName);
            statement.setString(4, message);
            statement.setLong(5, Instant.now().toEpochMilli());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save mail.", exception);
        }
    }

    public synchronized List<MailRecord> getUndeliveredMail(UUID recipientUuid) {
        List<MailRecord> records = new ArrayList<>();

        try (PreparedStatement statement = database.connection().prepareStatement("""
            SELECT id, sender_name, message, sent_epoch_ms
            FROM mail
            WHERE recipient_uuid = ? AND delivered = 0
            ORDER BY sent_epoch_ms ASC
            """)) {
            statement.setString(1, recipientUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(new MailRecord(
                        resultSet.getLong("id"),
                        resultSet.getString("sender_name"),
                        resultSet.getString("message"),
                        resultSet.getLong("sent_epoch_ms")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load pending mail.", exception);
        }

        return records;
    }

    public synchronized void markMailDelivered(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }

        try (PreparedStatement statement = database.connection().prepareStatement("""
            UPDATE mail
            SET delivered = 1
            WHERE id = ?
            """)) {
            for (Long id : ids) {
                statement.setLong(1, id);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to mark mail as delivered.", exception);
        }
    }

    public synchronized NicknameUpdateResult updateNickname(UUID uuid, String nickname) {
        if (nickname != null && !nickname.isBlank()) {
            String normalizedNickname = nickname.toLowerCase(Locale.ROOT);
            if (playerNameExists(normalizedNickname)) {
                return NicknameUpdateResult.CONFLICTS_WITH_PLAYER_NAME;
            }
            if (nicknameExistsForAnotherPlayer(uuid, normalizedNickname)) {
                return NicknameUpdateResult.CONFLICTS_WITH_OTHER_NICKNAME;
            }
        }

        updateBooleanOrText("""
            UPDATE players
            SET nickname = ?
            WHERE uuid = ?
            """, uuid, nickname);
        return NicknameUpdateResult.UPDATED;
    }

    public synchronized void updateFlyMode(UUID uuid, boolean enabled) {
        updateBoolean("""
            UPDATE players
            SET fly_mode = ?
            WHERE uuid = ?
            """, uuid, enabled);
    }

    public synchronized void updateGodMode(UUID uuid, boolean enabled) {
        updateBoolean("""
            UPDATE players
            SET god_mode = ?
            WHERE uuid = ?
            """, uuid, enabled);
    }

    public synchronized void updateTeleportBlocked(UUID uuid, boolean enabled) {
        updateBoolean("""
            UPDATE players
            SET teleport_blocked = ?
            WHERE uuid = ?
            """, uuid, enabled);
    }

    public synchronized BigDecimal getBalance(UUID uuid) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
            SELECT balance
            FROM players
            WHERE uuid = ?
            """)) {
            statement.setString(1, uuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return BigDecimal.ZERO.setScale(2);
                }

                return settings.normalizeAmount(new BigDecimal(resultSet.getString("balance")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read a balance.", exception);
        }
    }

    public synchronized BalanceOperation setBalance(UUID uuid, BigDecimal amount) {
        BigDecimal normalized = settings.normalizeAmount(amount);

        try (PreparedStatement statement = database.connection().prepareStatement("""
            UPDATE players
            SET balance = ?
            WHERE uuid = ?
            """)) {
            statement.setString(1, normalized.toPlainString());
            statement.setString(2, uuid.toString());
            int updated = statement.executeUpdate();

            if (updated == 0) {
                return BalanceOperation.failure(normalized, BigDecimal.ZERO.setScale(2), "Account not found.");
            }

            return BalanceOperation.success(normalized, normalized);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to set a balance.", exception);
        }
    }

    public synchronized BalanceOperation deposit(UUID uuid, BigDecimal amount) {
        BigDecimal normalized = settings.normalizeAmount(amount);

        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            return BalanceOperation.failure(normalized, BigDecimal.ZERO.setScale(2), "Amount must be positive.");
        }

        BigDecimal current = getBalance(uuid);
        BigDecimal updated = settings.normalizeAmount(current.add(normalized));
        return setBalance(uuid, updated);
    }

    public synchronized BalanceOperation withdraw(UUID uuid, BigDecimal amount) {
        BigDecimal normalized = settings.normalizeAmount(amount);

        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            return BalanceOperation.failure(normalized, BigDecimal.ZERO.setScale(2), "Amount must be positive.");
        }

        BigDecimal current = getBalance(uuid);

        if (current.compareTo(normalized) < 0) {
            return BalanceOperation.failure(normalized, current, "Insufficient funds.");
        }

        BigDecimal updated = settings.normalizeAmount(current.subtract(normalized));
        return setBalance(uuid, updated);
    }

    public synchronized BalanceTransfer transfer(UUID senderUuid, UUID recipientUuid, BigDecimal amount) {
        BigDecimal normalized = settings.normalizeAmount(amount);

        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            return BalanceTransfer.failure(normalized, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), "Amount must be positive.");
        }

        Connection connection = null;
        boolean previousAutoCommit = true;

        try {
            connection = database.connection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            BigDecimal senderBalance = selectBalance(connection, senderUuid);
            BigDecimal recipientBalance = selectBalance(connection, recipientUuid);

            if (senderBalance == null || recipientBalance == null) {
                connection.rollback();
                return BalanceTransfer.failure(normalized, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), "Account not found.");
            }

            if (senderBalance.compareTo(normalized) < 0) {
                connection.rollback();
                return BalanceTransfer.failure(normalized, senderBalance, recipientBalance, "Insufficient funds.");
            }

            BigDecimal newSenderBalance = settings.normalizeAmount(senderBalance.subtract(normalized));
            BigDecimal newRecipientBalance = settings.normalizeAmount(recipientBalance.add(normalized));

            updateBalance(connection, senderUuid, newSenderBalance);
            updateBalance(connection, recipientUuid, newRecipientBalance);

            connection.commit();
            return BalanceTransfer.success(normalized, newSenderBalance, newRecipientBalance);
        } catch (SQLException exception) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            throw new IllegalStateException("Unable to transfer funds.", exception);
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // Keep the original result or failure path intact.
                }
            }
        }
    }

    public synchronized List<BalanceEntry> topBalances(int limit) {
        List<BalanceEntry> entries = new ArrayList<>();

        try (PreparedStatement statement = database.connection().prepareStatement("""
            SELECT last_known_name, balance
            FROM players
            ORDER BY CAST(balance AS REAL) DESC, last_known_name ASC
            LIMIT ?
            """)) {
            statement.setInt(1, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(new BalanceEntry(
                        resultSet.getString("last_known_name"),
                        settings.normalizeAmount(new BigDecimal(resultSet.getString("balance")))
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load baltop results.", exception);
        }

        return entries;
    }

    public synchronized void updateLastSeen(Player player) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
            UPDATE players
            SET last_known_name = ?, last_known_name_lower = ?, last_known_ip = COALESCE(?, last_known_ip), last_seen_epoch_ms = ?
            WHERE uuid = ?
            """)) {
            statement.setString(1, player.getName());
            statement.setString(2, player.getName().toLowerCase(Locale.ROOT));
            statement.setString(3, extractIp(player));
            statement.setLong(4, Instant.now().toEpochMilli());
            statement.setString(5, player.getUniqueId().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update last seen.", exception);
        }
    }

    private StoredPlayer readPlayer(ResultSet resultSet) throws SQLException {
        return new StoredPlayer(
            UUID.fromString(resultSet.getString("uuid")),
            resultSet.getString("last_known_name"),
            resultSet.getString("last_known_ip"),
            settings.normalizeAmount(new BigDecimal(resultSet.getString("balance"))),
            resultSet.getString("nickname"),
            resultSet.getInt("god_mode") == 1,
            resultSet.getInt("fly_mode") == 1,
            resultSet.getInt("teleport_blocked") == 1,
            resultSet.getLong("last_seen_epoch_ms")
        );
    }

    private boolean playerNameExists(String normalizedName) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
            SELECT 1
            FROM players
            WHERE last_known_name_lower = ?
            LIMIT 1
            """)) {
            statement.setString(1, normalizedName);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to validate a nickname against player names.", exception);
        }
    }

    private boolean nicknameExistsForAnotherPlayer(UUID excludedUuid, String normalizedNickname) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
            SELECT 1
            FROM players
            WHERE uuid <> ?
              AND nickname IS NOT NULL
              AND LOWER(nickname) = ?
            LIMIT 1
            """)) {
            statement.setString(1, excludedUuid.toString());
            statement.setString(2, normalizedNickname);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to validate a nickname against other nicknames.", exception);
        }
    }

    private void updateBoolean(String sql, UUID uuid, boolean value) {
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setInt(1, value ? 1 : 0);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update a boolean player field.", exception);
        }
    }

    private void updateBooleanOrText(String sql, UUID uuid, String value) {
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, value);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update a text player field.", exception);
        }
    }

    private BigDecimal selectBalance(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT balance
            FROM players
            WHERE uuid = ?
            """)) {
            statement.setString(1, uuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return settings.normalizeAmount(new BigDecimal(resultSet.getString("balance")));
            }
        }
    }

    private void updateBalance(Connection connection, UUID uuid, BigDecimal newBalance) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE players
            SET balance = ?
            WHERE uuid = ?
            """)) {
            statement.setString(1, newBalance.toPlainString());
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        }
    }

    private @Nullable String extractIp(Player player) {
        if (player.getAddress() == null) {
            return null;
        }

        InetAddress address = player.getAddress().getAddress();
        return address == null ? null : address.getHostAddress();
    }

    public enum NicknameUpdateResult {
        UPDATED,
        CONFLICTS_WITH_PLAYER_NAME,
        CONFLICTS_WITH_OTHER_NICKNAME
    }
}
