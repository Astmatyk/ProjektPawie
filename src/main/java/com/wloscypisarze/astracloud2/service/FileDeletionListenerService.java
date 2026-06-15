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

                // mówimy Postgresowi: "Od teraz słucham tego kanału"
                stmt.execute("LISTEN file_deletion_channel");
                stmt.close();

                // unwrappujemy połączenie do natywnego sterownika PostgreSQL, żeby mieć dostęp do powiadomień
                PGConnection pgConn = connection.unwrap(PGConnection.class);

                System.out.println("[Listener] Uruchomiono asynchroniczny nasłuch bazy danych");

                while (running) {
                    // sprawdzamy powiadomienia.
                    // parametr 1000 oznacza, że wątek bezpiecznie "śpi" i czeka max 1 sekundę na sygnał.
                    // dzięki temu obciążenie procesora wynosi równe 0%.
                    PGNotification[] notifications = pgConn.getNotifications(1000);

                    if (notifications != null) {
                        for (PGNotification notification : notifications) {
                            // Tu ląduje tekst, który w triggerze przekazałeś jako OLD.path
                            String filePath = notification.getParameter();
                            System.out.println("[Sygnał z bazy] Wykryto usunięcie! Sprzątam dysk dla: " + filePath);

                            try {
                                File fileOnDisk = new File(filePath);
                                if (fileOnDisk.exists()) {
                                    if (fileOnDisk.delete()) {
                                        System.out.println("️Plik fizyczny został usunięty z dysku.");
                                    } else {
                                        System.out.println("Istnieje plik, ale Java nie mogła go skasować.");
                                    }
                                } else {
                                    System.out.println("Pliku nie ma już na dysku.");
                                }
                            } catch (Exception e) {
                                System.err.println("Błąd podczas usuwania pliku z dysku: " + e.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // zabezpieczenie na wypadek zerwania połączenia z bazą (np. restart bazy danych)
                System.err.println("Wątek nasłuchujący bazy został przerwany: " + e.getMessage());
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