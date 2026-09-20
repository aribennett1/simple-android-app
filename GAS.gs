const FOLDER_ID = '1v7woKfo1EMmUs64hRMnn7Jen3cje_vBe';

function doGet() {
  const folder = DriveApp.getFolderById(FOLDER_ID);
  const files = folder.getFiles();
  const images = [];

  while (files.hasNext()) {
    const file = files.next();
    const mime = file.getMimeType();

    if (!mime.startsWith('image/')) continue;

    images.push({
      id: file.getId(),
      name: file.getName(),
      mimeType: mime,
      updated: file.getLastUpdated().toISOString(),
      url: `https://drive.google.com/uc?export=download&id=${file.getId()}`
    });
  }

  return ContentService
    .createTextOutput(JSON.stringify(images))
    .setMimeType(ContentService.MimeType.JSON);
}
