//manageAccount.js

const emailForm = document.getElementById('emailForm');
const passwordForm = document.getElementById('passwordForm');

// funkcja pomocnicza do pobierania nagłówków CSRF
function getCsrfHeaders() {
    const tokenEl = document.querySelector('meta[name="_csrf"]');
    const headerEl = document.querySelector('meta[name="_csrf_header"]');
    if (tokenEl && headerEl) {
        return { [headerEl.getAttribute('content')]: tokenEl.getAttribute('content') };
    }
    return {};
}

function toggleSection(section) {
    const view = document.getElementById(`${section}-view`);
    const edit = document.getElementById(`${section}-edit`);

    view.classList.toggle('hidden');
    edit.classList.toggle('hidden');

    if(section === 'password' && !edit.classList.contains('hidden')) {
        document.getElementById('oldPassword').value = '';
        document.getElementById('newPassword').value = '';
    }
}

function deleteOwnAccount() {
    if (confirm("CZY NA PEWNO CHCESZ USUNĄĆ SWOJE KONTO?\nTa operacja jest nieodwracalna, a wszystkie Twoje pliki w chmurze zostaną skasowane.")) {

        fetch("/api/account/delete", {
            method: "POST",
            headers: {
                ...getCsrfHeaders()
            }
        })
            .then(res => {
                if (res.ok) {
                    alert("Twoje konto zostało usunięte. Nastąpi wylogowanie.");
                    window.location.href = "/";
                } else {
                    alert("Wystąpił błąd podczas usuwania konta.");
                }
            });
    }
}

emailForm.addEventListener("submit", (event) => {
    event.preventDefault();

    const newEmail = document.getElementById("newEmail").value;

    fetch("/api/account/email", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            ...getCsrfHeaders()
        },
        body: JSON.stringify({ newEmail })
    })
        .then(async res => {
            if (res.ok) {
                emailForm.innerHTML = "";
                alert("Email został pomyślnie zmieniony.");
                location.reload();
            } else {
                alert("Błąd: " + await res.text());
                location.reload();
            }
        });

    emailForm.innerHTML = `<div class="spinner-container">
          <div class="spinner"></div>
      </div>`;
});

passwordForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const oldPassword = document.getElementById("oldPassword").value;
    const newPassword = document.getElementById("newPassword").value;

    if (!oldPassword || !newPassword) return alert("Wypełnij oba pola haseł!");

    fetch("/api/account/password", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            ...getCsrfHeaders()
        },
        body: JSON.stringify({ oldPassword, newPassword })
    })
        .then(async res => {
            if (res.ok) {
                passwordForm.innerHTML = "";
                alert("Hasło zostało pomyślnie zmienione.");
                location.reload();
            } else {
                alert("Błąd: " + await res.text());
                location.reload();
            }
        });

    passwordForm.innerHTML = `<div class="spinner-container">
          <div class="spinner"></div>
      </div>`;
});
