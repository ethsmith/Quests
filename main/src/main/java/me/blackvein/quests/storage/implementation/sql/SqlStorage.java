/*******************************************************************************************************
 * Continued by PikaMug (formerly HappyPikachu) with permission from _Blackvein_. All rights reserved.
 * 
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN
 * NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************************************/

package me.blackvein.quests.storage.implementation.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import me.blackvein.quests.Quest;
import me.blackvein.quests.Quester;
import me.blackvein.quests.Quests;
import me.blackvein.quests.storage.implementation.StorageImplementation;
import me.blackvein.quests.storage.implementation.sql.connection.ConnectionFactory;

public class SqlStorage implements StorageImplementation {
    private static final String PLAYER_SELECT = "SELECT lastknownname, hasjournal, questpoints FROM '{prefix}players' WHERE uuid=?";
    private static final String PLAYER_SELECT_USERNAME = "SELECT lastknownname FROM '{prefix}players' WHERE uuid=? LIMIT 1";
    private static final String PLAYER_UPDATE_USERNAME = "UPDATE '{prefix}players' SET lastknownname=? WHERE uuid=?";
    private static final String PLAYER_INSERT = "INSERT INTO '{prefix}players' (uuid, lastknownname, hasjournal, questpoints) "
            + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE uuid=uuid, lastknownname=lastknownname, hasjournal=hasjournal, questpoints=questpoints";
    private static final String PLAYER_DELETE = "DELETE FROM '{prefix}players' WHERE uuid=?";
    
    private static final String PLAYER_CURRENT_QUESTS_SELECT_BY_UUID = "SELECT questid, stageNum FROM '{prefix}player_currentquests' WHERE uuid=?";
    private static final String PLAYER_CURRENT_QUESTS_DELETE_FOR_UUID_AND_QUEST = "DELETE FROM '{prefix}player_currentquests' WHERE uuid=? AND questid=?";
    private static final String PLAYER_CURRENT_QUESTS_INSERT = "INSERT INTO '{prefix}player_currentquests' (uuid, questid, stageNum) "
            + "VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE uuid=uuid, questid=questid, stageNum=stageNum";
    private static final String PLAYER_CURRENT_QUESTS_DELETE = "DELETE FROM '{prefix}player_currentquests' WHERE uuid=?";
    
    private static final String PLAYER_COMPLETED_QUESTS_SELECT_BY_UUID = "SELECT questid FROM '{prefix}player_completedquests' WHERE uuid=?";
    private static final String PLAYER_COMPLETED_QUESTS_DELETE_FOR_UUID_AND_QUEST = "DELETE FROM '{prefix}player_completedquests' WHERE uuid=? AND questid=?";
    private static final String PLAYER_COMPLETED_QUESTS_INSERT = "INSERT INTO '{prefix}player_completedquests' (uuid, questid) "
            + "VALUES(?, ?) ON DUPLICATE KEY UPDATE uuid=uuid, questid=questid";
    private static final String PLAYER_COMPLETED_QUESTS_DELETE = "DELETE FROM '{prefix}player_completedquests' WHERE uuid=?";
    
    private static final String PLAYER_REDOABLE_QUESTS_SELECT_BY_UUID = "SELECT questid, time, amount FROM '{prefix}player_redoablequests' WHERE uuid=?";
    private static final String PLAYER_REDOABLE_QUESTS_DELETE_FOR_UUID_AND_QUEST = "DELETE FROM '{prefix}player_redoablequests' WHERE uuid=? AND questid=?";
    private static final String PLAYER_REDOABLE_QUESTS_INSERT = "INSERT INTO '{prefix}player_redoablequests' (uuid, questid, time, amount) "
            + "VALUES(?, ?, ?, ?) ON DUPLICATE KEY UPDATE uuid=uuid, questid=questid, time=time, amount=amount";
    private static final String PLAYER_REDOABLE_QUESTS_DELETE = "DELETE FROM '{prefix}player_redoablequests' WHERE uuid=?";

    private final Quests plugin;
    private final ConnectionFactory connectionFactory;
    private final Function<String, String> statementProcessor;

    public SqlStorage(final Quests plugin, final ConnectionFactory connectionFactory, final String tablePrefix) {
        this.plugin = plugin;
        this.connectionFactory = connectionFactory;
        this.statementProcessor = connectionFactory.getStatementProcessor().compose(s -> s.replace("{prefix}", tablePrefix));
    }

    @Override
    public Quests getPlugin() {
        return plugin;
    }

    @Override
    public String getImplementationName() {
        return connectionFactory.getImplementationName();
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public Function<String, String> getStatementProcessor() {
        return statementProcessor;
    }

    @Override
    public void init() throws Exception {
        connectionFactory.init(plugin);
        
        try (Connection c = connectionFactory.getConnection()) {
            final String[] queries = new String[4];
            queries[0] = "CREATE TABLE IF NOT EXISTS `" + statementProcessor.apply("{prefix}players") 
                    + "` (`uuid` VARCHAR(36) NOT NULL, "
                    + "`lastknownname` VARCHAR(16) NOT NULL, "
                    + "`hasjournal` BOOL NOT NULL, "
                    + "`questpoints` BIGINT NOT NULL, "
                    + "PRIMARY KEY (`uuid`)"
                    + ") DEFAULT CHARSET = utf8mb4";
            queries[1] = "CREATE TABLE IF NOT EXISTS `" + statementProcessor.apply("{prefix}player_currentquests")
                    + "` (id INT AUTO_INCREMENT NOT NULL,"
                    + "`uuid` VARCHAR(36) NOT NULL, "
                    + "`questid` VARCHAR(100) NOT NULL,"
                    + "`stageNum` INT NOT NULL,"
                    + "PRIMARY KEY (`id`),"
                    + "UNIQUE KEY (`uuid`, `questid`)"
                    + ") DEFAULT CHARSET = utf8mb4";
            queries[2] = "CREATE TABLE IF NOT EXISTS `" + statementProcessor.apply("{prefix}player_completedquests")
                    + "` (id INT AUTO_INCREMENT NOT NULL,"
                    + "`uuid` VARCHAR(36) NOT NULL, "
                    + "`questid` VARCHAR(100) NOT NULL,"
                    + "PRIMARY KEY (`id`),"
                    + "UNIQUE KEY (`uuid`, `questid`)"
                    + ") DEFAULT CHARSET = utf8mb4";
            queries[3] = "CREATE TABLE IF NOT EXISTS `" + statementProcessor.apply("{prefix}player_redoablequests")
                    + "` (id INT AUTO_INCREMENT NOT NULL,"
                    + "`uuid` VARCHAR(36) NOT NULL, "
                    + "`questid` VARCHAR(100) NOT NULL,"
                    + "`time` BIGINT NOT NULL,"
                    + "`amount` INT NOT NULL,"
                    + "PRIMARY KEY (`id`),"
                    + "UNIQUE KEY (`uuid`, `questid`)"
                    + ") DEFAULT CHARSET = utf8mb4";
            try (Statement s = c.createStatement()) {
                for (final String query : queries) {
                    try {
                        s.execute(query);
                    } catch (final SQLException e) {
                        if (e.getMessage().contains("Unknown character set")) {
                            s.execute(query.replace("utf8mb4", "utf8"));
                        } else {
                            throw e;
                        }
                    }
                }
            }
        }
    }
    
    @Override
    public void close() {
        try {
            connectionFactory.close();
        } catch (final Exception e) {
            this.plugin.getLogger().severe("Problem occurred while closing SQL storage");
            e.printStackTrace();
        }
    }
    
    @Override
    public Quester loadQuesterData(final UUID uniqueId) throws Exception {
        final Quester quester = plugin.getQuester(uniqueId);
        if (quester == null) {
            return null;
        }
        try (Connection c = connectionFactory.getConnection()) {
            if (uniqueId != null) {
                try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_SELECT))) {
                    ps.setString(1, uniqueId.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            quester.hasJournal = rs.getBoolean("hasjournal");
                            quester.setQuestPoints(rs.getInt("questpoints"));
                        }
                    }
                }
                quester.setCurrentQuests(getQuesterCurrentQuests(uniqueId));
                quester.setCompletedQuests(getQuesterCompletedQuests(uniqueId));
                quester.setCompletedTimes(getQuesterCompletedTimes(uniqueId));
                quester.setAmountsCompleted(getQuesterAmountsCompleted(uniqueId));
            }
        }
        return quester;
    }

    @Override
    public void saveQuesterData(final Quester quester) throws Exception {
        final UUID uniqueId = quester.getUUID();
        final String lastknownname = quester.getOfflinePlayer().getName();
        final String oldlastknownname = getQuesterLastKnownName(uniqueId);
        final Set<String> currentQuests = quester.getCurrentQuests().keySet().stream().map(Quest::getId).collect(Collectors.toSet());
        final Set<String> oldCurrentQuests = getQuesterCurrentQuests(uniqueId).keySet().stream().map(Quest::getId).collect(Collectors.toSet());
        oldCurrentQuests.removeAll(currentQuests);
        final Set<String> completedQuests = quester.getCompletedQuests().stream().map(Quest::getId).collect(Collectors.toSet());
        final Set<String> oldCompletedQuests = getQuesterCompletedQuests(uniqueId).stream().map(Quest::getId).collect(Collectors.toSet());
        oldCompletedQuests.removeAll(completedQuests);
        final Set<String> redoableQuests = quester.getCompletedTimes().keySet().stream().map(Quest::getId).collect(Collectors.toSet());
        final Set<String> oldRedoableQuests = getQuesterCompletedTimes(uniqueId).keySet().stream().map(Quest::getId).collect(Collectors.toSet());
        oldRedoableQuests.removeAll(redoableQuests);
        
        try (final Connection c = connectionFactory.getConnection()) {
            if (oldlastknownname != null && !lastknownname.equals(oldlastknownname)) {
                try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_UPDATE_USERNAME))) {
                    ps.setString(1, lastknownname);
                    ps.setString(2, uniqueId.toString());
                    ps.execute();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_INSERT))) {
                    ps.setString(1, uniqueId.toString());
                    ps.setString(2, lastknownname);
                    ps.setBoolean(3, quester.hasJournal);
                    ps.setInt(4, quester.getQuestPoints());
                    ps.execute();
                }
            }
            
            if (!oldCurrentQuests.isEmpty()) {
                for (final String questId : oldCurrentQuests) {
                    try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_CURRENT_QUESTS_DELETE_FOR_UUID_AND_QUEST))) {
                        ps.setString(1, uniqueId.toString());
                        ps.setString(2, questId);
                        ps.execute();
                    }
                }
            } else {
                for (final Entry<Quest, Integer> entry : quester.getCurrentQuests().entrySet()) {
                    try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_CURRENT_QUESTS_INSERT))) {
                        ps.setString(1, uniqueId.toString());
                        ps.setString(2, entry.getKey().getId());
                        ps.setInt(3, entry.getValue());
                        ps.execute();
                    }
                }
            }
            
            if (!oldCompletedQuests.isEmpty()) {
                for (final String questId : oldCompletedQuests) {
                    try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_COMPLETED_QUESTS_DELETE_FOR_UUID_AND_QUEST))) {
                        ps.setString(1, uniqueId.toString());
                        ps.setString(2, questId);
                        ps.execute();
                    }
                }
            } else {
                for (final Quest quest : quester.getCompletedQuests()) {
                    try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_COMPLETED_QUESTS_INSERT))) {
                        ps.setString(1, uniqueId.toString());
                        ps.setString(2, quest.getId());
                        ps.execute();
                    }
                }
            }
            
            if (!oldRedoableQuests.isEmpty()) {
                for (final String questId : oldRedoableQuests) {
                    try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_REDOABLE_QUESTS_DELETE_FOR_UUID_AND_QUEST))) {
                        ps.setString(1, uniqueId.toString());
                        ps.setString(2, questId);
                        ps.execute();
                    }
                }
            } else {
                for (final Entry<Quest, Long> entry : quester.getCompletedTimes().entrySet()) {
                    final int amount = quester.getAmountsCompleted().get(entry.getKey());
                    try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_REDOABLE_QUESTS_INSERT))) {
                        //System.out.println("Attempting to update with amount of " + amount);
                        ps.setString(1, uniqueId.toString());
                        ps.setString(2, entry.getKey().getId());
                        ps.setLong(3, entry.getValue());
                        ps.setInt(4, amount);
                        ps.execute();
                    }
                }
            }
        }
    }

    @Override
    public void deleteQuesterData(final UUID uniqueId) throws Exception {
        try (Connection c = connectionFactory.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_DELETE))) {
                ps.setString(1, uniqueId.toString());
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_CURRENT_QUESTS_DELETE))) {
                ps.setString(1, uniqueId.toString());
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_COMPLETED_QUESTS_DELETE))) {
                ps.setString(1, uniqueId.toString());
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_REDOABLE_QUESTS_DELETE))) {
                ps.setString(1, uniqueId.toString());
                ps.execute();
            }
        }
    }

    @Override
    public String getQuesterLastKnownName(final UUID uniqueId) throws Exception {
        try (Connection c = connectionFactory.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_SELECT_USERNAME))) {
                ps.setString(1, uniqueId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("lastknownname");
                    }
                }
            }
        }
        return null;
    }
    
    public ConcurrentHashMap<Quest, Integer> getQuesterCurrentQuests(final UUID uniqueId) throws Exception {
        final ConcurrentHashMap<Quest, Integer> currentQuests = new ConcurrentHashMap<Quest, Integer>();
        try (Connection c = connectionFactory.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_CURRENT_QUESTS_SELECT_BY_UUID))) {
                ps.setString(1, uniqueId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        currentQuests.put(plugin.getQuestById(rs.getString("questid")), rs.getInt("stageNum"));
                    }
                }
            }
        }
        return currentQuests;
    }
    
    public LinkedList<Quest> getQuesterCompletedQuests(final UUID uniqueId) throws Exception {
        final LinkedList<Quest> completedQuests = new LinkedList<Quest>();
        try (Connection c = connectionFactory.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_COMPLETED_QUESTS_SELECT_BY_UUID))) {
                ps.setString(1, uniqueId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        completedQuests.add(plugin.getQuestById(rs.getString("questid")));
                    }
                }
            }
        }
        return completedQuests;
    }
    
    public ConcurrentHashMap<Quest, Long> getQuesterCompletedTimes(final UUID uniqueId) throws Exception {
        final ConcurrentHashMap<Quest, Long> completedTimes = new ConcurrentHashMap<Quest, Long>();
        try (Connection c = connectionFactory.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_REDOABLE_QUESTS_SELECT_BY_UUID))) {
                ps.setString(1, uniqueId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        completedTimes.put(plugin.getQuestById(rs.getString("questid")), rs.getLong("time"));
                    }
                }
            }
        }
        return completedTimes;
    }
    
    public ConcurrentHashMap<Quest, Integer> getQuesterAmountsCompleted(final UUID uniqueId) throws Exception {
        final ConcurrentHashMap<Quest, Integer> amountsCompleted = new ConcurrentHashMap<Quest, Integer>();
        try (Connection c = connectionFactory.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(statementProcessor.apply(PLAYER_REDOABLE_QUESTS_SELECT_BY_UUID))) {
                ps.setString(1, uniqueId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        amountsCompleted.put(plugin.getQuestById(rs.getString("questid")), rs.getInt("amount"));
                    }
                }
            }
        }
        return amountsCompleted;
    }
}