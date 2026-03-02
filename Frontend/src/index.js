let serviceNum = -1;


//connect to the server as soon as stuff is loaded
window.onload = function() {
    console.log("establish connection to server");
    establishConnection();
}

function establishConnection() {
    fetch('http://54.225.145.40:5001/connect')  // Server address
        .then(response => response.json())
        .then(data => {
        console.log("Connection successful:", data);
    })
        .catch(error => {
        console.error("Connection error:", error);
    });
}

function uploadFile() {
    if(service == -1) {
        alert("no service selected");
    }
    const fileInput = document.getElementById("File_Input");
    const file = fileInput.files[0];

    if(!file) {
        alert("Please select a file");
        return;
    }

    //Base 64 conversion
    const reader = new FileReader();
    reader.onload = function(event) {
        const base64Data = event.target.result.split(',')[1]

        const data = {
            service: serviceNum,//1-6
            filename: file.name,//doesn't matter
            base64: base64Data//file data
        };

        sendToServer(data);
    };

    reader.readAsDataURL(file);
}

function sendToServer(data) {
    fetch('http://54.225.145.40:PORT/upload', {  // Replace with your server IP and port
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
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
