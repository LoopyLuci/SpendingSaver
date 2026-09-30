export type Status = 'online' | 'offline' | 'checking'

function StatusBadge({ status }: { status: Status }) {
  const colors = { online: 'bg-green-500', offline: 'bg-red-500', checking: 'bg-yellow-500' }
  return (
    <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 dark:bg-gray-700">
      <span className={`w-2 h-2 rounded-full ${colors[status]}`} />
      {status}
    </span>
  )
}

export function StatusBar({
  pythonStatus,
  goStatus,
  onStartPython,
  onStopPython,
  onStartGo,
  onStopGo,
}: {
  pythonStatus: Status
  goStatus: Status
  onStartPython: () => void
  onStopPython: () => void
  onStartGo: () => void
  onStopGo: () => void
}) {
  return (
    <div className="flex items-center gap-4 text-sm">
      <div className="flex items-center gap-2">
        <span className="text-gray-500">Python</span>
        <StatusBadge status={pythonStatus} />
        <button onClick={onStartPython} className="px-2 py-1 border rounded text-xs">Start</button>
        <button onClick={onStopPython} className="px-2 py-1 border rounded text-xs">Stop</button>
      </div>
      <div className="flex items-center gap-2">
        <span className="text-gray-500">Go</span>
        <StatusBadge status={goStatus} />
        <button onClick={onStartGo} className="px-2 py-1 border rounded text-xs">Start</button>
        <button onClick={onStopGo} className="px-2 py-1 border rounded text-xs">Stop</button>
      </div>
    </div>
  )
}
