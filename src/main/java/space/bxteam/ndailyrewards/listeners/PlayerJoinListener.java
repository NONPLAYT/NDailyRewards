package space.bxteam.ndailyrewards.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import space.bxteam.ndailyrewards.NDailyRewards;
import space.bxteam.ndailyrewards.api.event.AutoClaimEvent;
import space.bxteam.ndailyrewards.api.event.PlayerReceiveReminderEvent;
import space.bxteam.ndailyrewards.managers.enums.Language;
import space.bxteam.ndailyrewards.managers.reward.RewardManager;
import space.bxteam.ndailyrewards.utils.LogUtil;
import space.bxteam.ndailyrewards.utils.Permissions;
import space.bxteam.ndailyrewards.utils.TextUtils;
import space.bxteam.ndailyrewards.utils.UpdateCheckerUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public class PlayerJoinListener implements Listener {
    private RewardManager rewardManager = NDailyRewards.getInstance().getRewardManager();

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        try (Connection conn = NDailyRewards.getInstance().getDatabase().dbSource.getConnection()) {
            String checkQuery = "SELECT COUNT(*) FROM `data` WHERE uuid = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                checkStmt.setString(1, uuid.toString());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return;
                    }
                }
            }

            String insertQuery = "INSERT INTO `data` (uuid, next_time, next_day) VALUES (?, ?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                long nextTime = getUnixTimeForNextDay();
                insertStmt.setString(1, uuid.toString());
                insertStmt.setLong(2, nextTime);
                insertStmt.setInt(3, 0);
                insertStmt.executeUpdate();
            }
        } catch (SQLException e) {
            LogUtil.log("Could not create initial player data: " + e.getMessage(), LogUtil.LogLevel.ERROR);
            e.printStackTrace();
        }

        if (NDailyRewards.getInstance().getConfig().getBoolean("check-updates") && player.hasPermission(Permissions.UPDATE_NOTIFY)) {
            UpdateCheckerUtil.checkForUpdates().ifPresent(latestVersion -> {
                player.sendMessage(Language.PREFIX.asColoredString() + TextUtils.applyColor("&aA new update is available: " + latestVersion));
                player.sendMessage(Language.PREFIX.asColoredString() + TextUtils.applyColor("&aDownload here: &ehttps://modrinth.com/plugin/ndailyrewards/version/" + latestVersion));
            });
        }

        /* // TODO: fix this
        if (NDailyRewards.getInstance().getConfig().getBoolean("events.auto-claim-reward") && rewardManager.isRewardAvailable(player, rewardManager.getPlayerRewardData(uuid).getCurrentDay() + 1)) {
            int day = rewardManager.getPlayerRewardData(uuid).getCurrentDay() + 1;
            rewardManager.giveReward(player, day);
            AutoClaimEvent autoClaimEvent = new AutoClaimEvent(player, day);
            Bukkit.getPluginManager().callEvent(autoClaimEvent);
        }

        if (NDailyRewards.getInstance().getConfig().getBoolean("events.open-gui-when-available") && rewardManager.isRewardAvailable(player, rewardManager.getPlayerRewardData(uuid).getCurrentDay() + 1)) {
            NDailyRewards.getInstance().getMenuManager().openRewardsMenu(player);
        }

        if (NDailyRewards.getInstance().getConfig().getBoolean("events.notify-when-available") && rewardManager.isRewardAvailable(player, rewardManager.getPlayerRewardData(uuid).getCurrentDay() + 1)) {
            player.sendMessage(Language.PREFIX.asColoredString() + Language.EVENTS_NOTIFY_WHEN_AVAILABLE.asColoredString());
            PlayerReceiveReminderEvent reminderEvent = new PlayerReceiveReminderEvent(player, rewardManager.getPlayerRewardData(uuid).getCurrentDay() + 1);
            Bukkit.getPluginManager().callEvent(reminderEvent);
        }
         */
    }

    private long getUnixTimeForNextDay() {
        if (NDailyRewards.getInstance().getConfig().getBoolean("rewards.unlock-after-midnight")) {
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            return tomorrow.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
        } else {
            return Instant.now().plusSeconds(NDailyRewards.getInstance().getConfig().getInt("rewards.cooldown") * 3600L).getEpochSecond();
        }
    }
}
