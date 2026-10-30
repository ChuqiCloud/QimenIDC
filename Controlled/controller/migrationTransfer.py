"""
@Author: 星禾
@Description: 临时授信通道只允许下载该任务完成的单个归档，不公开管理 API。
@DateTime: 2026/10/30 12:00
"""
import re

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import Response, StreamingResponse

from service.MigrationTransfer import authorize_channel, observed_client_ip, stream_channel

transfer_router = APIRouter()


@transfer_router.api_route("/migration-transfer/{channel_id}/{filename}", methods=["GET", "HEAD"], include_in_schema=False)
def download_migration_archive(request: Request, channel_id: str, filename: str):
    token = request.headers.get("Authorization", "")
    try:
        client_ip = observed_client_ip(request)
    except (ValueError, AttributeError) as error:
        raise HTTPException(status_code=403, detail="invalid transfer source") from error
    data, path = authorize_channel(channel_id, filename, token, client_ip)
    offset, end = 0, data["size"] - 1
    status = 200
    headers = {"Accept-Ranges": "bytes", "ETag": '"' + data["sha256"] + '"',
               "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff"}
    byte_range = request.headers.get("Range")
    if byte_range:
        match = re.fullmatch(r"bytes=(\d+)-(\d*)", byte_range)
        if not match:
            raise HTTPException(status_code=416, detail="invalid archive range")
        offset = int(match.group(1))
        end = min(int(match.group(2)), end) if match.group(2) else end
        if offset > end:
            raise HTTPException(status_code=416, detail="archive range outside the file",
                                headers={"Content-Range": f"bytes */{data['size']}"})
        status = 206
        headers["Content-Range"] = f"bytes {offset}-{end}/{data['size']}"
    count = end - offset + 1
    headers["Content-Length"] = str(count)
    if request.method == "HEAD":
        return Response(status_code=status, headers=headers, media_type="application/octet-stream")
    return StreamingResponse(stream_channel(channel_id, filename, token, client_ip, path, offset, count),
                             status_code=status, headers=headers, media_type="application/octet-stream")
