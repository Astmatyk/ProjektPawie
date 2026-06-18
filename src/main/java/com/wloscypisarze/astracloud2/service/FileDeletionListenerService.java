package com.wloscypisarze.astracloud2.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.Statement;

@Service
public class FileDeletionListenerService {

    private final DataSource dataSource;
    private Connection connection;
    private Thread listenerThread;
    private volatile boolean running = true;

    public FileDeletionListenerService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void startListening() {
        // uruchamiamy nasłuchiwanie w osobnym wątku, żeby całkowicie odciążyć główną aplikację
        listenerThread = new Thread(() -> {
            try {
                // otwieramy dedykowane, stałe połączenie z bazą danych
                connection = dataSource.getConnection();
                Statement stmt = connection.createStatement();

                // nasłuchiwanie kanału
                stmt.execute("LISTEN file_deletion_channel");
                stmt.close();

                // unwrappujemy połączenie do natywnego sterownika PostgreSQL, żeby mieć dostęp do powiadomień
                PGConnection pgConn = connection.unwrap(PGConnection.class);

                System.out.println("[FileDeletionListenerService] Uruchomiono listener");

                while (running) {
                    // sprawdzamy powiadomienia
                    // wątek czeka na sygnał maks 1 sekunde
                    PGNotification[] notifications = pgConn.getNotifications(1000);

                    if (notifications != null) {
                        for (PGNotification notification : notifications) {
                            // wyłapywanie ścieżki pliku do usunięcia
                            String filePath = notification.getParameter();
                            System.out.println("[FileDeletionListenerService] Sprzątam: " + filePath);

                            try {
                                File fileOnDisk = new File(filePath);
                                if (fileOnDisk.exists()) {
                                    if (fileOnDisk.delete()) {
                                        System.out.println("️[FileDeletionListenerService] Plik został usunięty z dysku.");
                                    } else {
                                        System.out.println("[FileDeletionListenerService] Istnieje plik, ale nie udało się go skasować.");
                                    }
                                } else {
                                    System.out.println("[FileDeletionListenerService] Pliku nie ma już na dysku.");
                                }
                            } catch (Exception e) {
                                System.err.println("[FileDeletionListenerService] Błąd podczas usuwania pliku z dysku: " + e.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // zabezpieczenie na wypadek zerwania połączenia z bazą (np. restart bazy danych)
                System.err.println("[FileDeletionListenerService] Listener został przerwany: " + e.getMessage());
            }
        });

        listenerThread.setName("Postgres-LISTEN-Thread");
        listenerThread.setDaemon(true); // wątek zamknie się automatycznie wraz z wyłączeniem aplikacji
        listenerThread.start();
    }

    @PreDestroy
    public void stopListening() {
        running = false;
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            System.out.println("Zakończono nasłuchiwanie bazy danych.");
        } catch (Exception ignored) {}
    }
}