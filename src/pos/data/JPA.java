package pos.data;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public final class JPA {

    private static EntityManagerFactory emf;

    private JPA() { }

    public static synchronized EntityManagerFactory emf() {
        if (emf == null) {
            emf = Persistence.createEntityManagerFactory("posPU");
        }
        return emf;
    }

    public static EntityManager em() {
        return emf().createEntityManager();
    }

    public static synchronized void zatvori() {
        if (emf != null) {
            emf.close();
            emf = null;
        }
    }
}
