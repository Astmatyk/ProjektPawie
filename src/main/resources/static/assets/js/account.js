//account.js

const ascObj = document.getElementById('asc');
const descObj = document.getElementById('desc');

const uploadBtn = document.getElementById('uploadButton');
const uploadForm = document.getElementById('fileInput');
const uploadBar = document.getElementById('uploadBar');

const searchInput = document.getElementById('searchInput');
const searchForm = document.getElementById('sForm');

const createFolderBtn = document.getElementById('createFolderBtn');

let fileList = [];
let sortMode = 0;
let uploading = 0;

//stan nawigacji i historia
let currentFolderId = null;
let pathStack = [];

function showLoadingSpinner() {
    const flDiv = document.querySelector('.filelist');
    flDiv.innerHTML = `
        <div class="spinner-container">
            <div class="spinner"></div>
            <strong class="white">Ładowanie danych...</strong>
        </div>
        `;
}

function fetchList() {
    const folderQuery = currentFolderId ? `?folderId=${currentFolderId}` : '';
    const parentQuery = currentFolderId ? `?parentId=${currentFolderId}` : '';

    showLoadingSpinner();

    // pobieramy jednocześnie foldery i pliki
    Promise.all([
        fetch('/api/folders' + parentQuery).then(res => res.json()),
        fetch('/api/files' + folderQuery).then(res => res.json())
    ]).then(([folders, files]) => {

        // pliki mają typ file
        files.forEach(f => f.type = 'file');

        // buildlist buduje UI
        fileList = [...folders, ...sortList(files, sortMode)];
        buildList(fileList);
    }).catch(err => console.error("Błąd pobierania listy:", err));
}

function enterFolder(folderId) {
    pathStack.push(currentFolderId);
    currentFolderId = folderId;
    fetchList();
}

function goUp() {
    if (pathStack.length > 0) {
        currentFolderId = pathStack.pop();
        fetchList();
    }
}

// Tworzenie folderu
function createFolder() {
    const folderName = prompt("Podaj nazwę nowego folderu:");
    if (!folderName || folderName.trim() === '') return;

    fetch('/api/folders/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            folderName: folderName,
            parentId: currentFolderId
        })
    })
        .then(res => res.json())
        .then(result => {
            if (result.status === 'success') {
                fetchList();
            } else {
                alert('Błąd: ' + result.message);
            }
        });
}

// uploadowanie
function uploadFile() {
    const file = uploadForm.files[0];
    if (!file) return;

    //walidacja długości nazwy pliku
    //zgodnie z tym co wprowadziliśmy na backendzie
    if (file.name.length > 150) {
        alert("Nazwa pliku jest za długa.\nMaksymalna ilość: 150 znaków.");
        uploadForm.value = null;
        return;
    }

    console.log("Rozpoczynam przesyłanie...");
    uploadBtn.innerHTML = 'Przesyłanie...';
    uploading = 1;

    // zmieńmy to na fetch w wolnej chwili
    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/api/upload');

    uploadBar.style.height = '5px';
    uploadBar.style.width = 0;
    uploadBtn.style.backgroundColor = 'gray';
    uploadBtn.style.width = '110.5px';

    xhr.upload.onprogress = function(e) {
        if (e.lengthComputable) {
            const percentComplete = Math.round((e.loaded / e.total) * 100);
            uploadBar.style.width = (percentComplete-10) + '%';
        }
    };

    xhr.onload = function() {
        if (xhr.status == 200) {
            fetchList();
            uploadBtn.innerHTML = 'Przesłano!';
            uploadBtn.style.backgroundColor = 'green';
            uploadBar.style.height = 0;
            updateStorageUsage();
            setTimeout(() => {
                uploadBtn.innerHTML = 'Wyślij';
                uploadBtn.style.backgroundColor = 'purple';
                uploadBtn.style.width = '76px';
                uploadForm.value = null;
                uploading = 0;
            }, 1000)
        } else {
            if(xhr.status == 413) alert("Brak miejsca na koncie!\nWykup lepszy pakiet już dziś");
            uploadBtn.innerHTML = 'Błąd!';
            uploadBtn.style.backgroundColor = 'red';
            uploadBtn.style.width = '76px';
            uploadBar.style.height = 0;
            console.log("Błąd przesyłania: "+xhr.status);
            uploadForm.value = null;
            uploading = 0;
        }
    };

    xhr.onerror = function() {
        uploadBtn.innerHTML = 'Błąd!';
        uploadBtn.style.backgroundColor = 'red';
        uploadBtn.style.width = '76px';
        uploadBar.style.height = 0;
        console.log("Błąd przesyłania: błąd sieci");
        uploadForm.value = null;
        uploading = 0;
    }

    const formData = new FormData();
    formData.append('file', file);
    if (currentFolderId) {
        formData.append('folderId', currentFolderId);
    }
    xhr.send(formData);
}

// usuwanie!
function deleteFile(fileId, button) {
    //R.I.P. stary komunikat
    if (!confirm("Na pewno?")) return;
    console.log("Usuwam ID " + fileId);

    fetch('/api/delete', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ id: fileId })
    })
        .then(res => res.json())
        .then(result => {
            if (result.status === 'ok') {
                button.closest('.listItem').remove();
                fileList = fileList.filter(file => String(file.id) !== String(fileId));
                updateStorageUsage();
            } else {
                console.log("Błąd usuwania " + result.message);
                alert('Błąd: ' + result.message);
            }
        });
}

function deleteFolder(fileId, button) {
    if (!confirm("Na pewno?")) return;
    console.log("Usuwam ID " + fileId);

    fetch('/api/folders/delete', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ id: fileId })
    })
        .then(res => res.json())
        .then(result => {
            if (result.status === 'ok') {
                button.closest('.listItem').remove();
                fileList = fileList.filter(file => String(file.id) !== String(fileId));
                updateStorageUsage();
            } else {
                console.log("Błąd usuwania " + result.message);
                alert('Błąd: ' + result.message);
            }
        });
}

// zmiana nazwy!
function renameFile(fileId, oldName) {
    const newName = prompt("Wprowadź nową nazwę pliku:", oldName);
    if (!newName || newName === oldName || newName.trim() === '') return;
    if (newName.length > 150) {
        alert("Nazwa pliku jest za długa.\nMaksymalna ilość: 150 znaków.");
        uploadForm.value = null;
        return;
    }

    console.log("[file] Zmieniam nazwę ID " + fileId + " na " + newName);

    fetch('/api/rename', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ id: fileId, newName: newName })
    })
        .then(res => res.json())
        .then(result => {
            if (result.status === 'ok') {
                //ponownie pobieramy listę plików
                fetchList();
            } else {
                console.log("Błąd zmiany nazwy: " + result.message);
                alert('Błąd: ' + result.message);
            }
        });
}

function renameFolder(fileId, oldName) {
    const newName = prompt("Wprowadź nową nazwę folderu:", oldName);
    if (!newName || newName === oldName || newName.trim() === '') return;
    if (newName.length > 150) {
        alert("Nazwa folderu jest za długa.\nMaksymalna ilość: 150 znaków.");
        uploadForm.value = null;
        return;
    }

    console.log("[folder] Zmieniam nazwę ID " + fileId + " na " + newName);

    fetch('/api/folders/rename', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ id: fileId, newName: newName })
    })
        .then(res => res.json())
        .then(result => {
            if (result.status === 'ok') {
                //ponownie pobieramy listę plików
                fetchList();
            } else {
                console.log("Błąd zmiany nazwy: " + result.message);
                alert('Błąd: ' + result.message);
            }
        });
}

function shareFile(fileId) {
    fetch(`/api/share/${fileId}`, {
        method: 'POST'
    })
        .then(res => res.json())
        .then(result => {
            if (result.status === 'ok') {
                const shareUrl = window.location.origin + '/share/' + result.token;
                prompt("Skopiuj link do udostępnienia:", shareUrl);
            } else {
                alert('Błąd: ' + result.message);
            }
        })
        .catch(err => {
            console.error("Błąd udostępniania:", err);
            alert("Wystąpił błąd podczas generowania linku.");
        });
}

//jak się okazuje można łatwo przesłać plik o nazwie z tagami html
function escapeHTML(str) {
    return str.replace(/[&<>'"]/g,
        tag => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            "'": '&#39;',
            '"': '&quot;'
        }[tag])
    );
}

function getIconForExtension(ext) {
    if (!ext) return `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-file-earmark" viewBox="0 0 16 16"><path d="M14 4.5V14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V2a2 2 0 0 1 2-2h5.5zm-3 0A1.5 1.5 0 0 1 9.5 3V1H4a1 1 0 0 0-1 1v12a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1V4.5z"/></svg>`;

    const extension = ext.toLowerCase();

    // słownik z ikonami SVG dla różnych typów plików
    const icons = {
        // obrazy
        'png': `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-file-earmark-image" viewBox="0 0 16 16">
                        <path d="M6.502 7a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3"/>
                        <path d="M14 14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V2a2 2 0 0 1 2-2h5.5L14 4.5zM4 1a1 1 0 0 0-1 1v10l2.224-2.224a.5.5 0 0 1 .61-.075L8 11l2.157-3.02a.5.5 0 0 1 .76-.063L13 10V4.5h-2A1.5 1.5 0 0 1 9.5 3V1z"/>
                    </svg>`,
        'jpg': `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-file-earmark-image-fill" viewBox="0 0 16 16">
                        <path d="M4 0h5.293A1 1 0 0 1 10 .293L13.707 4a1 1 0 0 1 .293.707v5.586l-2.73-2.73a1 1 0 0 0-1.52.127l-1.889 2.644-1.769-1.062a1 1 0 0 0-1.222.15L2 12.292V2a2 2 0 0 1 2-2m5.5 1.5v2a1 1 0 0 0 1 1h2zm-1.498 4a1.5 1.5 0 1 0-3 0 1.5 1.5 0 0 0 3 0"/>
                        <path d="M10.564 8.27 14 11.708V14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-.293l3.578-3.577 2.56 1.536 2.426-3.395z"/>
                    </svg>`,
        'jpeg': `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-file-earmark-image-fill" viewBox="0 0 16 16">
                         <path d="M4 0h5.293A1 1 0 0 1 10 .293L13.707 4a1 1 0 0 1 .293.707v5.586l-2.73-2.73a1 1 0 0 0-1.52.127l-1.889 2.644-1.769-1.062a1 1 0 0 0-1.222.15L2 12.292V2a2 2 0 0 1 2-2m5.5 1.5v2a1 1 0 0 0 1 1h2zm-1.498 4a1.5 1.5 0 1 0-3 0 1.5 1.5 0 0 0 3 0"/>
                         <path d="M10.564 8.27 14 11.708V14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-.293l3.578-3.577 2.56 1.536 2.426-3.395z"/>
                     </svg>`,
        'gif': `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-file-earmark-image-fill" viewBox="0 0 16 16">
                         <path d="M4 0h5.293A1 1 0 0 1 10 .293L13.707 4a1 1 0 0 1 .293.707v5.586l-2.73-2.73a1 1 0 0 0-1.52.127l-1.889 2.644-1.769-1.062a1 1 0 0 0-1.222.15L2 12.292V2a2 2 0 0 1 2-2m5.5 1.5v2a1 1 0 0 0 1 1h2zm-1.498 4a1.5 1.5 0 1 0-3 0 1.5 1.5 0 0 0 3 0"/>
                         <path d="M10.564 8.27 14 11.708V14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-.293l3.578-3.577 2.56 1.536 2.426-3.395z"/>
                     </svg>`,
        'webp': `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-file-earmark-image-fill" viewBox="0 0 16 16">
                         <path d="M4 0h5.293A1 1 0 0 1 10 .293L13.707 4a1 1 0 0 1 .293.707v5.586l-2.73-2.73a1 1 0 0 0-1.52.127l-1.889 2.644-1.769-1.062a1 1 0 0 0-1.222.15L2 12.292V2a2 2 0 0 1 2-2m5.5 1.5v2a1 1 0 0 0 1 1h2zm-1.498 4a1.5 1.5 0 1 0-3 0 1.5 1.5 0 0 0 3 0"/>
                         <path d="M10.564 8.27 14 11.708V14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-.293l3.578-3.577 2.56 1.536 2.426-3.395z"/>
                     </svg>`,
        'svg': `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-file-earmark-image-fill" viewBox="0 0 16 16">
                         <path d="M4 0h5.293A1 1 0 0 1 10 .293L13.707 4a1 1 0 0 1 .293.707v5.586l-2.73-2.73a1 1 0 0 0-1.52.127l-1.889 2.644-1.769-1.062a1 1 0 0 0-1.222.15L2 12.292V2a2 2 0 0 1 2-2m5.5 1.5v2a1 1 0 0 0 1 1h2zm-1.498 4a1.5 1.5 0 1 0-3 0 1.5 1.5 0 0 0 3 0"/>
                         <path d="M10.564 8.27 14 11.708V14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-.293l3.578-3.577 2.56 1.536 2.426-3.395z"/>
                     </svg>`,

        // dokumenty PDF
        'pdf': `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-file-earmark-pdf-fill" viewBox="0 0 16 16">
                        <path d="M5.523 12.424q.21-.124.459-.238a8 8 0 0 1-.45.606c-.28.337-.498.516-.635.572l-.035.012a.3.3 0 0 1-.026-.044c-.056-.11-.054-.216.04-.36.106-.165.319-.354.647-.548m2.455-1.647q-.178.037-.356.078a21 21 0 0 0 .5-1.05 12 12 0 0 0 .51.858q-.326.048-.654.114m2.525.939a4 4 0 0 1-.435-.41q.344.007.612.054c.317.057.466.147.518.209a.1.1 0 0 1 .026.064.44.44 0 0 1-.06.2.3.3 0 0 1-.094.124.1.1 0 0 1-.069.015c-.09-.003-.258-.066-.498-.256M8.278 6.97c-.04.244-.108.524-.2.829a5 5 0 0 1-.089-.346c-.076-.353-.087-.63-.046-.822.038-.177.11-.248.196-.283a.5.5 0 0 1 .145-.04c.013.03.028.092.032.198q.008.183-.038.465z"/>
                        <path fill-rule="evenodd" d="M4 0h5.293A1 1 0 0 1 10 .293L13.707 4a1 1 0 0 1 .293.707V14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V2a2 2 0 0 1 2-2m5.5 1.5v2a1 1 0 0 0 1 1h2zM4.165 13.668c.09.18.23.343.438.419.207.075.412.04.58-.03.318-.13.635-.436.926-.786.333-.401.683-.927 1.021-1.51a11.7 11.7 0 0 1 1.997-.406c.3.383.61.713.91.95.28.22.603.403.934.417a.86.86 0 0 0 .51-.138c.155-.101.27-.247.354-.416.09-.181.145-.37.138-.563a.84.84 0 0 0-.2-.518c-.226-.27-.596-.4-.96-.465a5.8 5.8 0 0 0-1.335-.05 11 11 0 0 1-.98-1.686c.25-.66.437-1.284.52-1.794.036-.218.055-.426.048-.614a1.24 1.24 0 0 0-.127-.538.7.7 0 0 0-.477-.365c-.202-.043-.41 0-.601.077-.377.15-.576.47-.651.823-.073.34-.04.736.046 1.136.088.406.238.848.43 1.295a20 20 0 0 1-1.062 2.227 7.7 7.7 0 0 0-1.482.645c-.37.22-.699.48-.897.787-.21.326-.275.714-.08 1.103"/>
                    </svg>`,

        // dokumenty tekstowe
        'doc': `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-filetype-doc" viewBox="0 0 16 16">
                        <path fill-rule="evenodd" d="M14 4.5V14a2 2 0 0 1-2 2v-1a1 1 0 0 0 1-1V4.5h-2A1.5 1.5 0 0 1 9.5 3V1H4a1 1 0 0 0-1 1v9H2V2a2 2 0 0 1 2-2h5.5zm-7.839 9.166v.522q0 .384-.117.641a.86.86 0 0 1-.322.387.9.9 0 0 1-.469.126.9.9 0 0 1-.471-.126.87.87 0 0 1-.32-.386 1.55 1.55 0 0 1-.117-.642v-.522q0-.386.117-.641a.87.87 0 0 1 .32-.387.87.87 0 0 1 .471-.129q.264 0 .469.13a.86.86 0 0 1 .322.386q.117.255.117.641m.803.519v-.513q0-.565-.205-.972a1.46 1.46 0 0 0-.589-.63q-.381-.22-.917-.22-.533 0-.92.22a1.44 1.44 0 0 0-.589.627q-.204.406-.205.975v.513q0 .563.205.973.205.406.59.627.386.216.92.216.535 0 .916-.216.383-.22.59-.627.204-.41.204-.973M0 11.926v4h1.459q.603 0 .999-.238a1.45 1.45 0 0 0 .595-.689q.196-.45.196-1.084 0-.63-.196-1.075a1.43 1.43 0 0 0-.59-.68q-.395-.234-1.004-.234zm.791.645h.563q.371 0 .609.152a.9.9 0 0 1 .354.454q.118.302.118.753a2.3 2.3 0 0 1-.068.592 1.1 1.1 0 0 1-.196.422.8.8 0 0 1-.334.252 1.3 1.3 0 0 1-.483.082H.79V12.57Zm7.422.483a1.7 1.7 0 0 0-.103.633v.495q0 .369.103.627a.83.83 0 0 0 .298.393.85.85 0 0 0 .478.131.9.9 0 0 0 .401-.088.7.7 0 0 0 .273-.248.8.8 0 0 0 .117-.364h.765v.076a1.27 1.27 0 0 1-.226.674q-.205.29-.55.454a1.8 1.8 0 0 1-.786.164q-.54 0-.914-.216a1.4 1.4 0 0 1-.571-.627q-.194-.408-.194-.976v-.498q0-.568.197-.978.195-.411.571-.633.378-.223.911-.223.328 0 .607.097.28.093.489.272a1.33 1.33 0 0 1 .466.964v.073H9.78a.85.85 0 0 0-.12-.38.7.7 0 0 0-.273-.261.8.8 0 0 0-.398-.097.8.8 0 0 0-.475.138.87.87 0 0 0-.301.398"/>
                    </svg>`,
        'docx': `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-filetype-docx" viewBox="0 0 16 16">
                        <path fill-rule="evenodd" d="M14 4.5V11h-1V4.5h-2A1.5 1.5 0 0 1 9.5 3V1H4a1 1 0 0 0-1 1v9H2V2a2 2 0 0 1 2-2h5.5zm-6.839 9.688v-.522a1.5 1.5 0 0 0-.117-.641.86.86 0 0 0-.322-.387.86.86 0 0 0-.469-.129.87.87 0 0 0-.471.13.87.87 0 0 0-.32.386 1.5 1.5 0 0 0-.117.641v.522q0 .384.117.641a.87.87 0 0 0 .32.387.9.9 0 0 0 .471.126.9.9 0 0 0 .469-.126.86.86 0 0 0 .322-.386 1.55 1.55 0 0 0 .117-.642m.803-.516v.513q0 .563-.205.973a1.47 1.47 0 0 1-.589.627q-.381.216-.917.216a1.86 1.86 0 0 1-.92-.216 1.46 1.46 0 0 1-.589-.627 2.15 2.15 0 0 1-.205-.973v-.513q0-.569.205-.975.205-.411.59-.627.386-.22.92-.22.535 0 .916.22.383.219.59.63.204.406.204.972M1 15.925v-3.999h1.459q.609 0 1.005.235.396.233.589.68.196.445.196 1.074 0 .634-.196 1.084-.197.451-.595.689-.396.237-.999.237zm1.354-3.354H1.79v2.707h.563q.277 0 .483-.082a.8.8 0 0 0 .334-.252q.132-.17.196-.422a2.3 2.3 0 0 0 .068-.592q0-.45-.118-.753a.9.9 0 0 0-.354-.454q-.237-.152-.61-.152Zm6.756 1.116q0-.373.103-.633a.87.87 0 0 1 .301-.398.8.8 0 0 1 .475-.138q.225 0 .398.097a.7.7 0 0 1 .273.26.85.85 0 0 1 .12.381h.765v-.073a1.33 1.33 0 0 0-.466-.964 1.4 1.4 0 0 0-.49-.272 1.8 1.8 0 0 0-.606-.097q-.534 0-.911.223-.375.222-.571.633-.197.41-.197.978v.498q0 .568.194.976.195.406.571.627.375.216.914.216.44 0 .785-.164t.551-.454a1.27 1.27 0 0 0 .226-.674v-.076h-.765a.8.8 0 0 1-.117.364.7.7 0 0 1-.273.248.9.9 0 0 1-.401.088.85.85 0 0 1-.478-.131.83.83 0 0 1-.298-.393 1.7 1.7 0 0 1-.103-.627zm5.092-1.76h.894l-1.275 2.006 1.254 1.992h-.908l-.85-1.415h-.035l-.852 1.415h-.862l1.24-2.015-1.228-1.984h.932l.832 1.439h.035z"/>
                     </svg>`,
        'txt': `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-filetype-doc" viewBox="0 0 16 16">
                        <path fill-rule="evenodd" d="M14 4.5V14a2 2 0 0 1-2 2v-1a1 1 0 0 0 1-1V4.5h-2A1.5 1.5 0 0 1 9.5 3V1H4a1 1 0 0 0-1 1v9H2V2a2 2 0 0 1 2-2h5.5zm-7.839 9.166v.522q0 .384-.117.641a.86.86 0 0 1-.322.387.9.9 0 0 1-.469.126.9.9 0 0 1-.471-.126.87.87 0 0 1-.32-.386 1.55 1.55 0 0 1-.117-.642v-.522q0-.386.117-.641a.87.87 0 0 1 .32-.387.87.87 0 0 1 .471-.129q.264 0 .469.13a.86.86 0 0 1 .322.386q.117.255.117.641m.803.519v-.513q0-.565-.205-.972a1.46 1.46 0 0 0-.589-.63q-.381-.22-.917-.22-.533 0-.92.22a1.44 1.44 0 0 0-.589.627q-.204.406-.205.975v.513q0 .563.205.973.205.406.59.627.386.216.92.216.535 0 .916-.216.383-.22.59-.627.204-.41.204-.973M0 11.926v4h1.459q.603 0 .999-.238a1.45 1.45 0 0 0 .595-.689q.196-.45.196-1.084 0-.63-.196-1.075a1.43 1.43 0 0 0-.59-.68q-.395-.234-1.004-.234zm.791.645h.563q.371 0 .609.152a.9.9 0 0 1 .354.454q.118.302.118.753a2.3 2.3 0 0 1-.068.592 1.1 1.1 0 0 1-.196.422.8.8 0 0 1-.334.252 1.3 1.3 0 0 1-.483.082H.79V12.57Zm7.422.483a1.7 1.7 0 0 0-.103.633v.495q0 .369.103.627a.83.83 0 0 0 .298.393.85.85 0 0 0 .478.131.9.9 0 0 0 .401-.088.7.7 0 0 0 .273-.248.8.8 0 0 0 .117-.364h.765v.076a1.27 1.27 0 0 1-.226.674q-.205.29-.55.454a1.8 1.8 0 0 1-.786.164q-.54 0-.914-.216a1.4 1.4 0 0 1-.571-.627q-.194-.408-.194-.976v-.498q0-.568.197-.978.195-.411.571-.633.378-.223.911-.223.328 0 .607.097.28.093.489.272a1.33 1.33 0 0 1 .466.964v.073H9.78a.85.85 0 0 0-.12-.38.7.7 0 0 0-.273-.261.8.8 0 0 0-.398-.097.8.8 0 0 0-.475.138.87.87 0 0 0-.301.398"/>
                    </svg>`,

        // arkusze kalkulacyjne (bo czemu by nie)
        'xls': `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-calculator-fill" viewBox="0 0 16 16">
                        <path d="M2 2a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2zm2 .5v2a.5.5 0 0 0 .5.5h7a.5.5 0 0 0 .5-.5v-2a.5.5 0 0 0-.5-.5h-7a.5.5 0 0 0-.5.5m0 4v1a.5.5 0 0 0 .5.5h1a.5.5 0 0 0 .5-.5v-1a.5.5 0 0 0-.5-.5h-1a.5.5 0 0 0-.5.5M4.5 9a.5.5 0 0 0-.5.5v1a.5.5 0 0 0 .5.5h1a.5.5 0 0 0 .5-.5v-1a.5.5 0 0 0-.5-.5zM4 12.5v1a.5.5 0 0 0 .5.5h1a.5.5 0 0 0 .5-.5v-1a.5.5 0 0 0-.5-.5h-1a.5.5 0 0 0-.5.5M7.5 6a.5.5 0 0 0-.5.5v1a.5.5 0 0 0 .5.5h1a.5.5 0 0 0 .5-.5v-1a.5.5 0 0 0-.5-.5zM7 9.5v1a.5.5 0 0 0 .5.5h1a.5.5 0 0 0 .5-.5v-1a.5.5 0 0 0-.5-.5h-1a.5.5 0 0 0-.5.5m.5 2.5a.5.5 0 0 0-.5.5v1a.5.5 0 0 0 .5.5h1a.5.5 0 0 0 .5-.5v-1a.5.5 0 0 0-.5-.5zM10 6.5v1a.5.5 0 0 0 .5.5h1a.5.5 0 0 0 .5-.5v-1a.5.5 0 0 0-.5-.5h-1a.5.5 0 0 0-.5.5m.5 2.5a.5.5 0 0 0-.5.5v4a.5.5 0 0 0 .5.5h1a.5.5 0 0 0 .5-.5v-4a.5.5 0 0 0-.5-.5z"/>
                    </svg>`,
        'xlsx': `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-calculator" viewBox="0 0 16 16">
                        <path d="M12 1a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1zM4 0a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V2a2 2 0 0 0-2-2z"/>
                        <path d="M4 2.5a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 .5.5v2a.5.5 0 0 1-.5.5h-7a.5.5 0 0 1-.5-.5zm0 4a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-1a.5.5 0 0 1-.5-.5zm0 3a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-1a.5.5 0 0 1-.5-.5zm0 3a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-1a.5.5 0 0 1-.5-.5zm3-6a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-1a.5.5 0 0 1-.5-.5zm0 3a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-1a.5.5 0 0 1-.5-.5zm0 3a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-1a.5.5 0 0 1-.5-.5zm3-6a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-1a.5.5 0 0 1-.5-.5zm0 3a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 .5.5v4a.5.5 0 0 1-.5.5h-1a.5.5 0 0 1-.5-.5z"/>
                    </svg>`,

        // archiwa
        'zip': `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-archive" viewBox="0 0 16 16">
                        <path d="M0 2a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1v7.5a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 1 12.5V5a1 1 0 0 1-1-1zm2 3v7.5A1.5 1.5 0 0 0 3.5 14h9a1.5 1.5 0 0 0 1.5-1.5V5zm13-3H1v2h14zM5 7.5a.5.5 0 0 1 .5-.5h5a.5.5 0 0 1 0 1h-5a.5.5 0 0 1-.5-.5"/>
                    </svg>`,
        'rar': `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-archive-fill" viewBox="0 0 16 16">
                        <path d="M12.643 15C13.979 15 15 13.845 15 12.5V5H1v7.5C1 13.845 2.021 15 3.357 15zM5.5 7h5a.5.5 0 0 1 0 1h-5a.5.5 0 0 1 0-1M.8 1a.8.8 0 0 0-.8.8V3a.8.8 0 0 0 .8.8h14.4A.8.8 0 0 0 16 3V1.8a.8.8 0 0 0-.8-.8z"/>
                    </svg>`,

    };

    return icons[extension] || icons['txt'];
}


// lista plikow, budowanie html
function buildList(list) {
    const flDiv = document.querySelector('.filelist');

    const editIcon = `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-pencil-square" viewBox="0 0 16 16">
                        <path d="M15.502 1.94a.5.5 0 0 1 0 .706L14.459 3.69l-2-2L13.502.646a.5.5 0 0 1 .707 0l1.293 1.293zm-1.75 2.456-2-2L4.939 9.21a.5.5 0 0 0-.121.196l-.805 2.414a.25.25 0 0 0 .316.316l2.414-.805a.5.5 0 0 0 .196-.12l6.813-6.814z"/>
                        <path fill-rule="evenodd" d="M1 13.5A1.5 1.5 0 0 0 2.5 15h11a1.5 1.5 0 0 0 1.5-1.5v-6a.5.5 0 0 0-1 0v6a.5.5 0 0 1-.5.5h-11a.5.5 0 0 1-.5-.5v-11a.5.5 0 0 1 .5-.5H9a.5.5 0 0 0 0-1H2.5A1.5 1.5 0 0 0 1 2.5z"/>
                        </svg>`;
    const shareIcon = `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-share-fill" viewBox="0 0 16 16">
                            <path d="M11 2.5a2.5 2.5 0 1 1 .603 1.628l-6.718 3.12a2.5 2.5 0 0 1 0 1.504l6.718 3.12a2.5 2.5 0 1 1-.488.876l-6.718-3.12a2.5 2.5 0 1 1 0-3.256l6.718-3.12A2.5 2.5 0 0 1 11 2.5"/>
                            </svg>`;

    let html = '';
    if (currentFolderId !== null) {
        html += `
                    <div class="listItem" style="cursor: pointer; background: rgba(255,255,255,0.05);" onclick="goUp()">
                        <div class="item">
                            <strong>[..] W górę</strong>
                        </div>
                    </div>`;
    }

    //dla każdego pliku i folderu generujemy div'a
    html += list.map(item => {
        if (item.type === 'folder') {
            return `
            <div class="listItem" data-id="${item.id}">
                <div class="item">
                    <span style="cursor:pointer; display:flex; align-items:center;" onclick="enterFolder(${item.id})">
                        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-folder" viewBox="0 0 16 16">
                            <path d="M.54 3.87.5 3a2 2 0 0 1 2-2h3.672a2 2 0 0 1 1.414.586l.828.828A2 2 0 0 0 9.828 3h3.982a2 2 0 0 1 1.992 2.181l-.637 7A2 2 0 0 1 13.174 14H2.826a2 2 0 0 1-1.991-1.819l-.637-7a2 2 0 0 1 .342-1.31zM2.19 4a1 1 0 0 0-.996 1.09l.637 7a1 1 0 0 0 .995.91h10.348a1 1 0 0 0 .995-.91l.637-7A1 1 0 0 0 13.81 4zm4.69-1.707A1 1 0 0 0 6.172 2H2.5a1 1 0 0 0-1 .981l.006.139q.323-.119.684-.12h5.396z"/>
                        </svg>
                        <strong style="margin-left: 5px;">${escapeHTML(item.name)}</strong>
                    </span>
                    <button class="renameFolderButton" data-id="${item.id}" data-name="${escapeHTML(item.name)}">${editIcon}</button>
                    <button class="deleteFolderButton" data-id="${item.id}">Usuń</button>
                </div>
            </div>`;
        } else {
            const readable = item.date ? new Date(item.date * 1000).toLocaleString() : 'Brak daty';
            // pobranie ikony
            const fileIcon = getIconForExtension(item.extension);

            return `
            <div class="listItem" data-id="${item.id}">
                <span class="chdate">Data modyfikacji: ${readable}</span>
                <div class="item">
                    <span style="display: flex; align-items: center;">
                        ${fileIcon}
                        <a href="/api/download/${item.id}" target="_blank" style="margin-left: 5px;">${escapeHTML(item.name)}</a>
                    </span>
                    <button class="renameButton" data-id="${item.id}" data-name="${escapeHTML(item.name)}">${editIcon}</button>
                    <button class="shareButton" data-id="${item.id}">${shareIcon}</button>
                    <button class="deleteButton" data-id="${item.id}">Usuń</button>
                </div>
            </div>`;
        }
    }).join('');
    flDiv.innerHTML = `${html}`;

    //dodajemy eventy dla kazdego przycisku usuwania
    document.querySelectorAll('.deleteButton').forEach(button => {
        button.addEventListener('click', () => {
            const id = button.dataset.id;
            deleteFile(id, button);
        });
    });
    document.querySelectorAll('.deleteFolderButton').forEach(button => {
        button.addEventListener('click', () => {
            const id = button.dataset.id;
            deleteFolder(id, button);
        });
    });

    document.querySelectorAll('.shareButton').forEach(button => {
        button.addEventListener('click', () => {
            const id = button.dataset.id;
            shareFile(id);
        });
    });

    //eventy dla zmiany nazwy
    document.querySelectorAll('.renameButton').forEach(button => {
        button.addEventListener('click', () => {
            const id = button.dataset.id;
            const oldName = button.dataset.name;
            renameFile(id, oldName);
        });
    });
    document.querySelectorAll('.renameFolderButton').forEach(button => {
        button.addEventListener('click', () => {
            const id = button.dataset.id;
            const oldName = button.dataset.name;
            renameFolder(id, oldName);
        });
    });
}

// sortowanie
function sortList(fileList, sortMode) {
    const newlist = fileList.slice();

    //sortowanie rosnąco edycja javascript
    function asc(a, b) {
        if (a.name < b.name) return -1;
        if (a.name > b.name) return 1;
        return 0;
    }

    //sortowanie malejąco edycja javascript
    function desc(a, b) {
        if (a.name > b.name) return -1;
        if (a.name < b.name) return 1;
        return 0;
    }

    switch (sortMode) {
        case 0:
            newlist.sort(asc);
            ascObj.style.color = 'white';
            descObj.style.color = 'grey';
            break;
        case 1:
            newlist.sort(desc);
            ascObj.style.color = 'grey';
            descObj.style.color = 'white';
            break;
    }
    return newlist;
}

function searchList(searchString) {
    showLoadingSpinner();
    //backend sprawdza poprawność tokenu i usera zakodowanego w tokenie
    fetch(`/api/search?search=${encodeURIComponent(searchString)}`)
        .then(res => res.json())
        .then(files => {
            fileList = files;
            //sortujemy i budujemy div
            fileList = sortList(fileList, sortMode);
            buildList(fileList);
        })
    return fileList;
}

if(createFolderBtn) createFolderBtn.addEventListener('click', createFolder);

// gotowiec do konwersji formatow
function formatBytes(bytes, decimals = 2) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}

function updateStorageUsage() {
    fetch('/api/usage')
        .then(res => res.json())
        .then(data => {
            const used = data.usedBytes;
            const max = data.maxBytes;

            const percent = max > 0 ? (used / max) * 100 : 0;
            const percentFormatted = percent.toFixed(1);

            document.getElementById('storagePlanName').innerText = `Plan: ${data.planName}`;
            document.getElementById('storageText').innerText = `${formatBytes(used)} / ${formatBytes(max)}`;
            document.getElementById('storagePercent').innerText = `${percentFormatted}%`;

            const storageBar = document.getElementById('storageBar');
            const percentLabel = document.getElementById('storagePercent');

            storageBar.style.width = `${percent}%`;

            // zmiena koloru
            if (percent > 90) {
                storageBar.style.backgroundColor = '#f44336'; // czerwony
                percentLabel.style.color = '#f44336';
            } else if (percent > 75) {
                storageBar.style.backgroundColor = '#ff9800'; // żółty
                percentLabel.style.color = '#ff9800';
            } else {
                storageBar.style.backgroundColor = '#4caf50'; // zielony
                percentLabel.style.color = '#4caf50';
            }
        })
        .catch(err => console.error("Błąd pobierania statystyk dysku:", err));
}

// main
fetchList();
updateStorageUsage();

// eventy rozmaite
uploadBtn.addEventListener('click', () => {
    if (!uploading && uploadForm.files[0]) {
        uploadFile();
    }
});
ascObj.addEventListener('click', () => {
    buildList(sortList(fileList, 0));
})
descObj.addEventListener('click', () => {
    buildList(sortList(fileList, 1));
})
// searchForm.addEventListener('input', function(event) {
//     event.preventDefault();
//     console.log("Search: "+searchInput.value);
//     buildList(searchList(searchInput.value));
// });
searchInput.addEventListener('input', function(event) {
    console.log("Search: " + searchInput.value);
    searchList(searchInput.value);
});
