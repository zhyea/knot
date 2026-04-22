import { reactive } from "vue";
import { ElMessage } from "element-plus";
import { getHealth } from "../api/health";
import { formatDateTime } from "../utils/format";

export function useHealthStatus() {
  const health = reactive({
    status: "",
    serverTime: "",
    dbTime: ""
  });

  async function loadHealth() {
    try {
      const data = await getHealth();
      health.status = data.status;
      health.serverTime = formatDateTime(data.serverTime);
      health.dbTime = formatDateTime(data.dbTime);
    } catch (e) {
      ElMessage.error("后端不可达，请先启动 knot-server");
    }
  }

  return {
    health,
    loadHealth
  };
}
