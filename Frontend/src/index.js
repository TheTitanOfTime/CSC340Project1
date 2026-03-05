const serviceMap = {
    "Base64.html": 2,
    "CSVStats.html": 4,
    "Image-ASCII.html": 5,
};

const currentPage = window.location.pathname.split("/").pop();
const serviceNum = serviceMap[currentPage] ?? -1;
const GATEWAY = "http://54.225.145.40:5050";

window.onload = function() {
    console.log("establish connection to server");
    establishConnection();
    initDragAndDrop();

    if (serviceNum === 5) {
        document.getElementById("Upload_File").setAttribute("accept", "image/png, image/jpeg");
    }

    document.getElementById("uploadForm").addEventListener("submit", function(e) {
        e.preventDefault();
        uploadFile();
    });

    document.getElementById("Upload_File").addEventListener('change', function() {
        const label = document.querySelector('label[for="Upload_File"]');
        if (this.files[0]) updateLabelText(label, this.files[0].name);

        if (this.files[0]) {
            const file = this.files[0];
            if (serviceNum === 5 && !["image/png", "image/jpeg"].includes(file.type)) {
                alert("Please upload a PNG or JPG file");
                this.value = "";
                updateLabelText(label, null);
                return;
            }
            updateLabelText(label, file.name);
            showPreview(file);
        }
    });
}

function establishConnection() {
    fetch(GATEWAY + '/ping')
        .then(response => response.json())
        .then(data => { console.log("Connection successful:", data); })
        .catch(error => { console.error("Connection error:", error); });
}

function initDragAndDrop() {
    const label = document.querySelector('label[for="Upload_File"]');
    if (!label) return;

    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(evt => {
        label.addEventListener(evt, e => { e.preventDefault(); e.stopPropagation(); });
    });
    ['dragenter', 'dragover'].forEach(evt => {
        label.addEventListener(evt, () => label.classList.add('drag-over'));
    });
    ['dragleave', 'drop'].forEach(evt => {
        label.addEventListener(evt, () => label.classList.remove('drag-over'));
    });

    label.addEventListener('drop', function(e) {
        const file = e.dataTransfer.files[0];
        if (!file) return;

        const dt = new DataTransfer();
        dt.items.add(file);
        document.getElementById("Upload_File").files = dt.files;

        updateLabelText(label, file.name);
        showPreview(file);
    });
}

function updateLabelText(label, filename) {
    label.innerHTML = filename
        ? `<b>${filename}</b>`
        : 'Click or Drag <br> to Upload File';
}

function uploadFile() {
    if (serviceNum === -1) {
        alert("no service selected");
        return;
    }

    const fileInput = document.getElementById("Upload_File");
    const file = fileInput.files[0];
    if (!file) {
        alert("Please select a file");
        return;
    }

    if (serviceNum === 5 && !["image/png", "image/jpeg"].includes(file.type)) {
        alert("Please upload a PNG or JPG file");
        return;
    }

    const reader = new FileReader();
    reader.onload = function(event) {
        const base64Data = event.target.result.split(',')[1];
        const data = {
            service:  serviceNum,
            filename: file.name,
            base64:   base64Data
        };
        sendToServer(data);
    };
    reader.readAsDataURL(file);
}

function sendToServer(data) {
    fetch(GATEWAY + "/api/service", {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
    })
    .then(response => response.json())
    .then(responseData => {
        console.log('Server response:', responseData);
    })
    .catch(error => {
        console.error('Error sending file:', error);
    });
}
    
function showPreview(file) {
    const preview = document.getElementById("Image_Preview");
    if (!preview) return;
    const reader = new FileReader();
    reader.onload = e => {
        preview.src = e.target.result;
        preview.style.display = "block";
    };
    reader.readAsDataURL(file);
}