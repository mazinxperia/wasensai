import { UploadDropzone } from "@/components/viewer/UploadDropzone";
import { ViewerShell } from "@/components/viewer/ViewerShell";
import { ThemeProvider } from "@/context/theme";
import { useWaviewArchive } from "@/hooks/useWaviewArchive";

function ViewerApp() {
  const archive = useWaviewArchive();

  if (archive.status === "ready" && archive.summary) {
    return <ViewerShell archive={archive} />;
  }

  return (
    <UploadDropzone
      status={archive.status}
      progress={archive.progress}
      error={archive.error}
      onOpenFile={archive.openArchive}
    />
  );
}

function App() {
  return (
    <ThemeProvider>
      <ViewerApp />
    </ThemeProvider>
  );
}

export default App;
