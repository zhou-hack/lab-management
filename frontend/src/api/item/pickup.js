import request from '../request'

export function pagePickups(params) {
  return request.get('/item/pickups', { params })
}

export function getPickupDetail(id) {
  return request.get(`/item/pickups/${String(id)}`)
}
