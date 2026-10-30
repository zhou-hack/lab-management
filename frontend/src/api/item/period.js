import request from '../request'

export function listItemPeriods() {
  return request.get('/item/periods')
}
